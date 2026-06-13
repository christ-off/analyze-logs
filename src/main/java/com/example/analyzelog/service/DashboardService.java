package com.example.analyzelog.service;

import com.example.analyzelog.config.RefererFilterProperties;
import com.example.analyzelog.config.UriStemFilterProperties;
import com.example.analyzelog.config.UriStemGroupProperties;
import com.example.analyzelog.model.BotHumanDailyCount;
import com.example.analyzelog.model.BotUaRequest;
import com.example.analyzelog.model.BurstIp;
import com.example.analyzelog.model.CountryResultTypeCount;
import com.example.analyzelog.model.DailyResultTypeCount;
import com.example.analyzelog.model.FakeBrowserUa;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.model.NameResultTypeCount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("java:S2077") // dynamic SQL parts are static constants or parameterized — no user input is concatenated
@Service
public class DashboardService {

    private static final String COUNT_FIELD = "count";
    private static final String FIELD_FUNCTION = "function";
    private static final String FIELD_ERROR = "error";
    private static final String AND_SEPARATOR = " AND ";
    private static final String SQL_AND_INDENT = "  AND ";
    private static final String SQL_SELECT_UA_NAME = "SELECT ua_name as name,\n";
    private static final int UA_COUNTRIES_LIMIT = 10;
    private static final String RESULT_TYPE_EXCLUSION =
            "edge_response_result_type NOT IN ('Error'," + ResultTypeSql.FUNCTION_TYPE_LIST + ")";
    private static final String RESULT_TYPE_GROUP_EXPR =
            "CASE WHEN edge_response_result_type IN (" + ResultTypeSql.FUNCTION_TYPE_LIST + ") " +
            "THEN 'Filtered' ELSE edge_response_result_type END";
    private static final RowMapper<BotUaRequest> BOT_UA_REQUEST_MAPPER = (rs, i) -> {
        String iso = rs.getString("country");
        String countryName = (iso != null && !iso.isBlank())
                ? new java.util.Locale("", iso).getDisplayCountry(java.util.Locale.ENGLISH)
                : "-";
        return new BotUaRequest(
                Instant.parse(rs.getString("timestamp")),
                rs.getString("client_ip"),
                rs.getString("uri_stem"),
                rs.getString("result_type"),
                countryName,
                rs.getInt("status"));
    };
    private static final RowMapper<NameCount> NAME_COUNT_MAPPER =
            (rs, _) -> new NameCount(rs.getString("name"), rs.getLong(COUNT_FIELD));
    private static final RowMapper<NameResultTypeCount> NAME_RESULT_TYPE_COUNT_MAPPER =
            (rs, _) -> new NameResultTypeCount(
                    rs.getString("name"),
                    rs.getLong("hit"), rs.getLong("miss"),
                    rs.getLong(FIELD_FUNCTION), rs.getLong(FIELD_ERROR));
    private static final RowMapper<DailyResultTypeCount> DAILY_RESULT_TYPE_COUNT_MAPPER =
            (rs, _) -> new DailyResultTypeCount(
                    LocalDate.parse(rs.getString("day")),
                    rs.getLong("hit"), rs.getLong("miss"),
                    rs.getLong(FIELD_FUNCTION), rs.getLong(FIELD_ERROR));
    private static final RowMapper<CountryResultTypeCount> COUNTRY_RESULT_TYPE_COUNT_MAPPER =
            (rs, _) -> {
                String iso = rs.getString("code");
                return new CountryResultTypeCount(iso, resolveCountryLabel(iso),
                        rs.getLong("hit"), rs.getLong("miss"),
                        rs.getLong(FIELD_FUNCTION), rs.getLong(FIELD_ERROR));
            };
    private static final String URI_STEM_EXCLUSION_PREDICATE = "uri_stem NOT LIKE ?";
    private static final String RESULT_TYPE_SUMS = ResultTypeSql.RESULT_TYPE_SUMS;
    private static final String NOISE_EXCLUSION_CLAUSE =
            "NOT EXISTS (SELECT 1 FROM noise_filter nf" +
            " WHERE nf.ua_name = cloudfront_logs.ua_name AND nf.uri_stem = cloudfront_logs.uri_stem)";
    private static final String NOISE_EXCLUSION_CLAUSE_ALIASED =
            "  AND NOT EXISTS (SELECT 1 FROM noise_filter nf" +
            " WHERE nf.ua_name = c.ua_name AND nf.uri_stem = c.uri_stem)\n";
    private static final String BOT_FILTER_ALIASED =
            "  AND s.ua_group NOT IN ('AI Bots','Search Bots','Other Bots','Apps','Feed Readers')\n" +
            "  AND c.edge_response_result_type NOT IN ('Error'," + ResultTypeSql.FUNCTION_TYPE_LIST + ")\n" +
            NOISE_EXCLUSION_CLAUSE_ALIASED;
    private static final String LIMIT_PARAM = "LIMIT ?\n";
    private static final String GROUP_BY_UA_NAME = "GROUP BY ua_name\n";
    private final String sqlUriByResultType;
    private static final String SQL_URI_RESULT_TYPE_GROUP_ORDER =
            "GROUP BY name\n" + ResultTypeSql.ORDER_BY_TOTAL_DESC + LIMIT_PARAM;
    private static final String SQL_DAILY_SELECT = """
            SELECT date(timestamp) as day,
            """ + RESULT_TYPE_SUMS + """

            FROM cloudfront_logs
            WHERE timestamp BETWEEN ? AND ?
            """;
    private static final String SQL_DAILY_GROUP_ORDER = """
            GROUP BY day
            ORDER BY day
            """;
    private final JdbcTemplate jdbc;
    private final EdgeLocationResolver edgeLocationResolver;
    private final List<String> excludedExtensions;
    private final List<String> selfReferers;
    private final ReloadableRefererService refererService;
    private final Map<String, List<String>> groupPatterns;

    public DashboardService(JdbcTemplate jdbc, EdgeLocationResolver edgeLocationResolver,
                            UriStemFilterProperties uriStemFilterProperties,
                            RefererFilterProperties refererFilterProperties,
                            ReloadableRefererService refererService,
                            UriStemGroupProperties uriStemGroupProperties) {
        this.jdbc = jdbc;
        this.edgeLocationResolver = edgeLocationResolver;
        this.excludedExtensions = uriStemFilterProperties.excludedExtensions();
        this.selfReferers = refererFilterProperties.selfReferers();
        this.refererService = refererService;
        this.groupPatterns = uriStemGroupProperties.groups().stream()
                .collect(Collectors.toMap(
                        UriStemGroupProperties.Group::name,
                        UriStemGroupProperties.Group::patterns));
        this.sqlUriByResultType = "SELECT \n" +
                buildUriStemNameCase(uriStemGroupProperties.groups()) +
                RESULT_TYPE_SUMS + "\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n";
    }

    private static String buildUriStemNameCase(List<UriStemGroupProperties.Group> groups) {
        StringBuilder sb = new StringBuilder("CASE\n");
        for (var g : groups) {
            for (String pattern : g.patterns()) {
                sb.append("    WHEN LOWER(uri_stem) LIKE LOWER('").append(pattern).append("') THEN '")
                  .append(g.name()).append("'\n");
            }
        }
        sb.append("    ELSE uri_stem\nEND as name,\n");
        return sb.toString();
    }

    private Map.Entry<String, List<Object>> uriStemPredicate(String urlName) {
        List<String> patterns = groupPatterns.get(urlName);
        if (patterns != null) {
            String pred = patterns.stream()
                    .map(_ -> "LOWER(uri_stem) LIKE LOWER(?)")
                    .collect(Collectors.joining(" OR ", "(", ")"));
            return Map.entry(pred, List.copyOf(patterns));
        }
        return Map.entry("uri_stem = ?", List.of(urlName));
    }

    private String uriStemExclusionClause() {
        return excludedExtensions.stream()
                .map(_ -> URI_STEM_EXCLUSION_PREDICATE)
                .collect(Collectors.joining(AND_SEPARATOR));
    }

    private static String andClause(String clause) {
        return clause.isEmpty() ? "" : SQL_AND_INDENT + clause + "\n";
    }

    private static String resolveCountryLabel(String iso) {
        if (iso == null || iso.isBlank()) return iso;
        String display = Locale.of("", iso).getDisplayCountry(Locale.ENGLISH);
        return (display != null && !display.isBlank() && !display.equals(iso)) ? display : iso;
    }

    private String botExclusionClause() {
        return "ua_name != '(no user agent)'" +
               " AND ua_name NOT IN (" +
               "SELECT ua_name FROM static_ua" +
               " WHERE ua_group IN ('AI Bots','Search Bots','Other Bots','Apps','Feed Readers'))";
    }

    private String humanTrafficExclusionClause() {
        return Stream.of(botExclusionClause(), NOISE_EXCLUSION_CLAUSE, RESULT_TYPE_EXCLUSION)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(AND_SEPARATOR));
    }

    public List<NameCount> uaGroupCounts(Instant from, Instant to, boolean excludeBots) {
        var args = new ArrayList<>();
        args.add(from.toString());
        args.add(to.toString());

        String botFilter = excludeBots ? BOT_FILTER_ALIASED : "";

        String sql = "SELECT s.ua_group AS name, COUNT(*) AS count\n" +
                     "FROM cloudfront_logs c\n" +
                     "INNER JOIN static_ua s ON c.ua_name = s.ua_name\n" +
                     "WHERE c.timestamp BETWEEN ? AND ?\n" +
                     botFilter +
                     "GROUP BY s.ua_group\n" +
                     "ORDER BY count DESC";
        return jdbc.query(sql, NAME_COUNT_MAPPER, args.toArray());
    }

    public List<NameResultTypeCount> topBots(Instant from, Instant to, int limit) {
        String sql = "SELECT s.ua_name as name,\n" + RESULT_TYPE_SUMS + "\n" +
                "FROM cloudfront_logs c\n" +
                "INNER JOIN static_ua s ON c.ua_name = s.ua_name\n" +
                "WHERE c.timestamp BETWEEN ? AND ?\n" +
                "  AND s.ua_group IN ('AI Bots','Search Bots','Other Bots')\n" +
                "  AND c.uri_stem != '/robots.txt'\n" +
                "GROUP BY s.ua_name\n" +
                ResultTypeSql.ORDER_BY_TOTAL_DESC +
                LIMIT_PARAM;
        return jdbc.query(sql, NAME_RESULT_TYPE_COUNT_MAPPER, from.toString(), to.toString(), limit);
    }

    public List<NameResultTypeCount> topUserAgentsByResultType(Instant from, Instant to, int limit, boolean excludeBots) {
        String exclusion = excludeBots ? andClause(humanTrafficExclusionClause()) : "";
        String sql = SQL_SELECT_UA_NAME + RESULT_TYPE_SUMS + "\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                exclusion +
                GROUP_BY_UA_NAME +
                ResultTypeSql.ORDER_BY_TOTAL_DESC +
                LIMIT_PARAM;
        return jdbc.query(sql, NAME_RESULT_TYPE_COUNT_MAPPER, from.toString(), to.toString(), limit);
    }

    public List<CountryResultTypeCount> topCountriesByResultType(Instant from, Instant to, int limit, boolean excludeBots) {
        String exclusion = excludeBots ? andClause(humanTrafficExclusionClause()) : "";
        String sql = "SELECT country as code,\n" + RESULT_TYPE_SUMS + "\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                "  AND country IS NOT NULL\n" +
                exclusion +
                "GROUP BY country\n" +
                ResultTypeSql.ORDER_BY_TOTAL_DESC +
                LIMIT_PARAM;
        return jdbc.query(sql, COUNTRY_RESULT_TYPE_COUNT_MAPPER, from.toString(), to.toString(), limit);
    }

    public List<CountryResultTypeCount> topCountriesByFilteredRatio(Instant from, Instant to, int limit) {
        String sql = "SELECT country as code,\n" + RESULT_TYPE_SUMS + "\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                "  AND country IS NOT NULL\n" +
                "GROUP BY country\n" +
                "HAVING function > 0\n" +
                "ORDER BY CASE WHEN (hit + miss) = 0 THEN CAST(function AS REAL)\n" +
                "              ELSE CAST(function AS REAL) / (hit + miss) END DESC\n" +
                LIMIT_PARAM;
        return jdbc.query(sql, COUNTRY_RESULT_TYPE_COUNT_MAPPER, from.toString(), to.toString(), limit);
    }

    public List<NameResultTypeCount> countryTopUserAgentsByResultType(String countryCode, Instant from, Instant to, int limit, boolean excludeBots) {
        String exclusion = excludeBots ? andClause(humanTrafficExclusionClause()) : "";
        String sql = SQL_SELECT_UA_NAME + RESULT_TYPE_SUMS + "\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                "  AND country = ?\n" +
                exclusion +
                GROUP_BY_UA_NAME +
                ResultTypeSql.ORDER_BY_TOTAL_DESC +
                LIMIT_PARAM;
        return jdbc.query(sql, NAME_RESULT_TYPE_COUNT_MAPPER,
                from.toString(), to.toString(), countryCode, limit);
    }

    public List<NameCount> countryResultTypes(String countryCode, Instant from, Instant to, boolean excludeBots) {
        String exclusion = excludeBots ? andClause(humanTrafficExclusionClause()) : "";
        String sql = "SELECT " + RESULT_TYPE_GROUP_EXPR + " as name, COUNT(*) as count\n"
                + "FROM cloudfront_logs\n"
                + "WHERE timestamp BETWEEN ? AND ?\n"
                + "  AND country = ?\n"
                + exclusion
                + "GROUP BY name\n"
                + "ORDER BY count DESC\n";
        return jdbc.query(sql, NAME_COUNT_MAPPER, from.toString(), to.toString(), countryCode);
    }

    public List<NameResultTypeCount> countryUrlsByResultType(String countryCode, Instant from, Instant to, int limit, boolean excludeBots) {
        return urlsByResultType("country = ?", List.of(from.toString(), to.toString(), countryCode), limit, excludeBots);
    }

    public List<DailyResultTypeCount> countryRequestsPerDay(String countryCode, Instant from, Instant to, boolean excludeBots) {
        String exclusion = excludeBots ? andClause(humanTrafficExclusionClause()) : "";
        return queryDailyByResultType(SQL_DAILY_SELECT + "  AND country = ?\n" + exclusion + SQL_DAILY_GROUP_ORDER,
                from.toString(), to.toString(), countryCode);
    }

    public List<NameResultTypeCount> topUrlsByResultType(Instant from, Instant to, int limit, boolean excludeBots) {
        return urlsByResultType("", List.of(from.toString(), to.toString()), limit, excludeBots);
    }

    private List<NameResultTypeCount> urlsByResultType(String additionalFilter, List<Object> baseArgs, int limit, boolean excludeBots) {
        String exclusionClause = uriStemExclusionClause();
        String botClause = excludeBots ? humanTrafficExclusionClause() : "";
        String combinedFilter = Stream.of(additionalFilter, botClause)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(AND_SEPARATOR));
        String sql = sqlUriByResultType
                + andClause(combinedFilter)
                + andClause(exclusionClause)
                + SQL_URI_RESULT_TYPE_GROUP_ORDER;

        var args = new ArrayList<>(baseArgs);
        excludedExtensions.forEach(ext -> args.add("%." + ext.replaceFirst("^\\.", "")));
        args.add(limit);

        return jdbc.query(sql, NAME_RESULT_TYPE_COUNT_MAPPER, args.toArray());
    }

    public List<NameCount> topEdgeLocations(Instant from, Instant to, int limit) {
        return jdbc.query("""
                SELECT edge_location_iata as iata, COUNT(*) as count
                FROM cloudfront_logs
                WHERE timestamp BETWEEN ? AND ?
                  AND edge_location_iata IS NOT NULL
                GROUP BY edge_location_iata
                ORDER BY count DESC
                LIMIT ?
                """,
                (rs, _) -> new NameCount(edgeLocationResolver.resolveDisplay(rs.getString("iata")), rs.getLong(COUNT_FIELD)),
                from.toString(), to.toString(), limit);
    }

    public List<NameCount> platformCounts(Instant from, Instant to, boolean excludeBots) {
        String exclusion = excludeBots ? andClause(humanTrafficExclusionClause()) : "";
        String sql = """
                SELECT CASE
                    WHEN user_agent LIKE '%iPhone%' OR user_agent LIKE '%iPad%' OR user_agent LIKE '%iPod%' THEN 'iOS'
                    WHEN user_agent LIKE '%Android%' THEN 'Android'
                    WHEN user_agent LIKE '%Windows%' THEN 'Windows'
                    WHEN user_agent LIKE '%Macintosh%' OR user_agent LIKE '%Mac OS X%' THEN 'Mac'
                    WHEN user_agent LIKE '%Linux%' THEN 'Linux'
                    ELSE 'Other'
                END as name,
                COUNT(*) as count
                FROM cloudfront_logs
                WHERE timestamp BETWEEN ? AND ?
                """ + exclusion + """
                GROUP BY name
                ORDER BY count DESC
                """;
        return jdbc.query(sql, NAME_COUNT_MAPPER, from.toString(), to.toString());
    }

    public List<NameCount> topReferers(Instant from, Instant to, int limit, boolean excludeBots) {
        List<String> exclusionPatterns = selfReferers.stream()
                .flatMap(prefix -> selfExclusionPatterns(prefix).stream())
                .toList();
        String selfExclusionClause = exclusionPatterns.stream()
                .map(_ -> "referer NOT LIKE ?")
                .collect(Collectors.joining(AND_SEPARATOR));
        String botClause = excludeBots ? humanTrafficExclusionClause() : "";
        String sql = "SELECT referer as name, COUNT(*) as count\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                "  AND referer IS NOT NULL\n" +
                "  AND " + RESULT_TYPE_EXCLUSION + "\n" +
                andClause(selfExclusionClause) +
                andClause(botClause) +
                "GROUP BY referer\n" +
                "ORDER BY count DESC\n";

        var args = new ArrayList<>();
        args.add(from.toString());
        args.add(to.toString());
        exclusionPatterns.forEach(args::add);

        List<NameCount> raw = jdbc.query(sql, NAME_COUNT_MAPPER, args.toArray());

        return raw.stream()
                .map(entry -> Map.<String, Long>entry(normalizeReferer(entry.name()), entry.count()))
                .filter(pair -> pair.getKey() != null)
                .collect(Collectors.groupingBy(
                        Map.Entry<String, Long>::getKey,
                        Collectors.summingLong(Map.Entry<String, Long>::getValue)))
                .entrySet().stream()
                .map(e -> new NameCount(e.getKey(), e.getValue().longValue()))
                .sorted(Comparator.comparingLong(NameCount::count).reversed())
                .limit(limit)
                .toList();
    }

    private static List<String> selfExclusionPatterns(String configuredReferer) {
        String stripped = configuredReferer.endsWith("/")
                ? configuredReferer.substring(0, configuredReferer.length() - 1)
                : configuredReferer;
        List<String> patterns = new ArrayList<>();
        patterns.add(stripped + "%");
        try {
            String host = URI.create(configuredReferer).getHost();
            if (host != null) patterns.add(host + "%");
        } catch (IllegalArgumentException _) {
            // malformed URI — skip bare-domain pattern
        }
        return patterns;
    }

    private String normalizeReferer(String referer) {
        try {
            URI uri = URI.create(referer);
            String host = uri.getHost();
            if (host == null) {
                // schemeless referer (e.g. "www.google.com/path") — prepend scheme to parse host
                host = URI.create("https://" + referer).getHost();
            }
            if (host == null) return null;
            String h = host.startsWith("www.") ? host.substring(4) : host;
            for (RefererRule rule : refererService.getRules()) {
                if ((rule.domain() != null && h.equals(rule.domain()))
                        || (rule.domainStartsWith() != null && h.startsWith(rule.domainStartsWith()))
                        || (rule.domainEndsWith() != null && h.endsWith(rule.domainEndsWith()))) {
                    return rule.label();
                }
            }
            return h;
        } catch (IllegalArgumentException _) {
            // malformed URI — filter out
        }
        return null;
    }

    public List<NameResultTypeCount> uaRawUserAgents(String uaName, Instant from, Instant to, boolean excludeBots) {
        String exclusion = excludeBots ? andClause(RESULT_TYPE_EXCLUSION) : "";
        return jdbc.query("SELECT user_agent as name,\n" + RESULT_TYPE_SUMS + "\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                "  AND ua_name = ?\n" +
                exclusion +
                "GROUP BY user_agent\n" +
                ResultTypeSql.ORDER_BY_TOTAL_DESC,
                NAME_RESULT_TYPE_COUNT_MAPPER,
                from.toString(), to.toString(), uaName);
    }

    public List<NameCount> uaResultTypes(String uaName, Instant from, Instant to, boolean excludeBots) {
        String exclusion = excludeBots ? andClause(RESULT_TYPE_EXCLUSION) : "";
        return queryResultTypesByFilter("ua_name", uaName, from, to, exclusion);
    }

    public List<NameCount> uaCountries(String uaName, Instant from, Instant to, boolean excludeBots) {
        String exclusion = excludeBots ? andClause(RESULT_TYPE_EXCLUSION) : "";
        return jdbc.query("SELECT country as name, COUNT(*) as count\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                "  AND ua_name = ?\n" +
                exclusion +
                "GROUP BY country\n" +
                "ORDER BY count DESC\n" +
                "LIMIT " + UA_COUNTRIES_LIMIT + "\n",
                (rs, _) -> new NameCount(resolveCountryLabel(rs.getString("name")), rs.getLong(COUNT_FIELD)),
                from.toString(), to.toString(), uaName);
    }

    public List<NameResultTypeCount> uaUrlsByResultType(String uaName, Instant from, Instant to, int limit, boolean excludeBots) {
        return urlsByResultType("ua_name = ?", List.of(from.toString(), to.toString(), uaName), limit, excludeBots);
    }

    public List<DailyResultTypeCount> uaRequestsPerDay(String uaName, Instant from, Instant to, boolean excludeBots) {
        String exclusion = excludeBots ? andClause(RESULT_TYPE_EXCLUSION) : "";
        return queryDailyByResultType(SQL_DAILY_SELECT + "  AND ua_name = ?\n" + exclusion + SQL_DAILY_GROUP_ORDER,
                from.toString(), to.toString(), uaName);
    }

    public List<DailyResultTypeCount> requestsPerDay(Instant from, Instant to, boolean excludeBots) {
        String exclusion = excludeBots ? andClause(humanTrafficExclusionClause()) : "";
        String sql = SQL_DAILY_SELECT + exclusion + SQL_DAILY_GROUP_ORDER;
        return queryDailyByResultType(sql, from.toString(), to.toString());
    }

    private List<DailyResultTypeCount> queryDailyByResultType(String sql, Object... args) {
        return jdbc.query(sql, DAILY_RESULT_TYPE_COUNT_MAPPER, args);
    }

    // filterColumn is always a trusted Java constant, never user input
    private List<NameCount> queryResultTypesByFilter(String filterColumn, Object value, Instant from, Instant to, String extraClause) {
        String sql = "SELECT " + RESULT_TYPE_GROUP_EXPR + " as name, COUNT(*) as count\n"
                + "FROM cloudfront_logs\n"
                + "WHERE timestamp BETWEEN ? AND ?\n"
                + "  AND " + filterColumn + " = ?\n"
                + extraClause
                + "GROUP BY name\n"
                + "ORDER BY count DESC\n";
        return jdbc.query(sql, NAME_COUNT_MAPPER, from.toString(), to.toString(), value);
    }

    public List<NameCount> urlMatchingUriStems(String urlName, Instant from, Instant to, boolean excludeBots) {
        var entry = uriStemPredicate(urlName);
        String exclusion = excludeBots ? andClause(humanTrafficExclusionClause()) : "";
        String sql = "SELECT uri_stem as name, COUNT(*) as count\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                "  AND " + entry.getKey() + "\n" +
                exclusion +
                "GROUP BY uri_stem\n" +
                "ORDER BY count DESC\n";
        var args = new ArrayList<>();
        args.add(from.toString());
        args.add(to.toString());
        args.addAll(entry.getValue());
        return jdbc.query(sql, NAME_COUNT_MAPPER, args.toArray());
    }

    public List<CountryResultTypeCount> urlTopCountriesByResultType(String urlName, Instant from, Instant to, int limit, boolean excludeBots) {
        var entry = uriStemPredicate(urlName);
        String exclusion = excludeBots ? andClause(humanTrafficExclusionClause()) : "";
        String sql = "SELECT country as code,\n" + RESULT_TYPE_SUMS + "\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                "  AND country IS NOT NULL\n" +
                "  AND " + entry.getKey() + "\n" +
                exclusion +
                "GROUP BY country\n" +
                ResultTypeSql.ORDER_BY_TOTAL_DESC +
                LIMIT_PARAM;
        var args = new ArrayList<>();
        args.add(from.toString());
        args.add(to.toString());
        args.addAll(entry.getValue());
        args.add(limit);
        return jdbc.query(sql, COUNTRY_RESULT_TYPE_COUNT_MAPPER, args.toArray());
    }

    public List<NameResultTypeCount> urlTopUserAgentsByResultType(String urlName, Instant from, Instant to, int limit, boolean excludeBots) {
        var entry = uriStemPredicate(urlName);
        String exclusion = excludeBots ? andClause(humanTrafficExclusionClause()) : "";
        String sql = SQL_SELECT_UA_NAME + RESULT_TYPE_SUMS + "\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                "  AND " + entry.getKey() + "\n" +
                exclusion +
                GROUP_BY_UA_NAME +
                ResultTypeSql.ORDER_BY_TOTAL_DESC +
                LIMIT_PARAM;
        var args = new ArrayList<>();
        args.add(from.toString());
        args.add(to.toString());
        args.addAll(entry.getValue());
        args.add(limit);
        return jdbc.query(sql, NAME_RESULT_TYPE_COUNT_MAPPER, args.toArray());
    }

    public List<DailyResultTypeCount> urlRequestsPerDay(String urlName, Instant from, Instant to, boolean excludeBots) {
        var entry = uriStemPredicate(urlName);
        String exclusion = excludeBots ? andClause(humanTrafficExclusionClause()) : "";
        String sql = SQL_DAILY_SELECT + SQL_AND_INDENT + entry.getKey() + "\n" + exclusion + SQL_DAILY_GROUP_ORDER;
        var args = new ArrayList<>();
        args.add(from.toString());
        args.add(to.toString());
        args.addAll(entry.getValue());
        return queryDailyByResultType(sql, args.toArray());
    }

    public List<NameResultTypeCount> probableBots(Instant from, Instant to, int limit) {
        return jdbc.query("""
                SELECT c.user_agent as name,
                SUM(CASE WHEN uri_stem NOT LIKE '%.%' AND uri_stem != '/' AND edge_response_result_type = 'Hit' THEN 1 ELSE 0 END) AS hit,
                SUM(CASE WHEN uri_stem NOT LIKE '%.%' AND uri_stem != '/' AND edge_response_result_type = 'Miss' THEN 1 ELSE 0 END) AS miss,
                SUM(CASE WHEN uri_stem NOT LIKE '%.%' AND uri_stem != '/' AND edge_response_result_type IN ('FunctionGeneratedResponse','FunctionExecutionError','FunctionThrottledError') THEN 1 ELSE 0 END) AS function,
                SUM(CASE WHEN uri_stem NOT LIKE '%.%' AND uri_stem != '/' AND edge_response_result_type = 'Error' THEN 1 ELSE 0 END) AS error,
                SUM(CASE WHEN uri_stem NOT LIKE '%.%' AND uri_stem != '/' THEN 1 ELSE 0 END) AS extless_pages,
                SUM(CASE WHEN uri_stem LIKE '%.%' AND uri_stem NOT IN ('/robots.txt', '/sitemap.xml', '/ads.txt') THEN 1 ELSE 0 END) AS assets
                FROM cloudfront_logs c
                WHERE c.user_agent != ''
                  AND c.timestamp >= ? AND c.timestamp < ?
                GROUP BY c.user_agent
                HAVING extless_pages > 5 AND assets = 0
                ORDER BY hit + miss + function + error DESC
                LIMIT ?
                """,
                NAME_RESULT_TYPE_COUNT_MAPPER,
                from.toString(), to.toString(), limit);
    }

    public List<NameResultTypeCount> refererTopUrlsByResultType(String refererLabel, Instant from, Instant to, int limit, boolean excludeBots) {
        return urlsByResultType("referer LIKE ?", List.of(from.toString(), to.toString(), "%" + refererLabel + "%"), limit, excludeBots);
    }

    public List<DailyResultTypeCount> refererRequestsPerDay(String refererLabel, Instant from, Instant to, boolean excludeBots) {
        String exclusion = excludeBots ? andClause(humanTrafficExclusionClause()) : "";
        String sql = SQL_DAILY_SELECT +
                "  AND referer LIKE ?\n" +
                exclusion + SQL_DAILY_GROUP_ORDER;
        return queryDailyByResultType(sql, from.toString(), to.toString(), "%" + refererLabel + "%");
    }

    public List<BotUaRequest> requestsByUserAgent(String ua, Instant from, Instant to) {
        String sql = """
                SELECT timestamp, client_ip, uri_stem, country, status,
                       CASE WHEN edge_response_result_type IN (%s) THEN 'Filtered'
                            ELSE edge_response_result_type END as result_type
                FROM cloudfront_logs
                WHERE user_agent = ?
                  AND timestamp >= ? AND timestamp < ?
                ORDER BY timestamp DESC
                """.formatted(ResultTypeSql.FUNCTION_TYPE_LIST);
        return jdbc.query(sql, BOT_UA_REQUEST_MAPPER, ua, from.toString(), to.toString());
    }

    public List<BotHumanDailyCount> botHumanDailyCounts(Instant from, Instant to) {
        return jdbc.query("""
                SELECT date(c.timestamp) as day,
                    SUM(CASE WHEN s.ua_group IN ('AI Bots','Search Bots','Other Bots','Apps')
                             OR c.edge_response_result_type IN (%s) THEN 1 ELSE 0 END) as bots,
                    SUM(CASE WHEN s.ua_group NOT IN ('AI Bots','Search Bots','Other Bots','Apps')
                             AND s.ua_group != 'Unknown'
                             AND c.ua_name != '(no user agent)'
                             AND c.edge_response_result_type NOT IN (%s) THEN 1 ELSE 0 END) as humans
                FROM cloudfront_logs c
                INNER JOIN static_ua s ON c.ua_name = s.ua_name
                WHERE c.timestamp BETWEEN ? AND ?
                GROUP BY day
                ORDER BY day
                """.formatted(ResultTypeSql.FUNCTION_TYPE_LIST, ResultTypeSql.FUNCTION_TYPE_LIST),
                (rs, _) -> new BotHumanDailyCount(
                        LocalDate.parse(rs.getString("day")),
                        rs.getLong("bots"),
                        rs.getLong("humans")),
                from.toString(), to.toString());
    }

    // Browser-classified UAs active in nearly every hour of the day — humans show a
    // diurnal pattern, so round-the-clock activity means the browser UA is fake.
    public List<FakeBrowserUa> fakeBrowserUas(Instant from, Instant to, int limit) {
        return jdbc.query("""
                SELECT c.user_agent AS name, COUNT(*) AS count,
                       COUNT(DISTINCT strftime('%H', c.timestamp)) AS active_hours,
                       COUNT(DISTINCT date(c.timestamp)) AS days
                FROM cloudfront_logs c
                INNER JOIN static_ua s ON c.ua_name = s.ua_name
                WHERE s.ua_group = 'Browsers'
                  AND c.timestamp BETWEEN ? AND ?
                GROUP BY c.user_agent
                HAVING count >= 100 AND active_hours >= 22
                ORDER BY count DESC
                LIMIT ?
                """,
                (rs, _) -> new FakeBrowserUa(
                        rs.getString("name"),
                        rs.getLong(COUNT_FIELD),
                        rs.getLong("active_hours"),
                        rs.getLong("days")),
                from.toString(), to.toString(), limit);
    }

    // Browser-classified UAs requesting site config files — robots.txt, ads.txt, sitemap.xml
    public List<NameCount> browserConfigFetches(Instant from, Instant to, int limit) {
        return jdbc.query("""
                SELECT c.user_agent AS name, COUNT(*) AS count
                FROM cloudfront_logs c
                INNER JOIN static_ua s ON c.ua_name = s.ua_name
                WHERE s.ua_group = 'Browsers'
                  AND c.uri_stem IN ('/robots.txt', '/ads.txt', '/sitemap.xml')
                  AND c.timestamp BETWEEN ? AND ?
                GROUP BY c.user_agent
                ORDER BY count DESC
                LIMIT ?
                """,
                NAME_COUNT_MAPPER,
                from.toString(), to.toString(), limit);
    }

    // IPs firing at least 60 requests inside a single minute — far beyond human browsing.
    public List<BurstIp> burstIps(Instant from, Instant to, int limit) {
        return jdbc.query("""
                WITH per_min AS (
                    SELECT client_ip, strftime('%Y-%m-%dT%H:%M', timestamp) AS minute, COUNT(*) AS c
                    FROM cloudfront_logs
                    WHERE timestamp BETWEEN ? AND ?
                    GROUP BY client_ip, minute
                )
                SELECT client_ip, MAX(c) AS max_per_minute, SUM(c) AS total
                FROM per_min
                GROUP BY client_ip
                HAVING max_per_minute >= 60
                ORDER BY max_per_minute DESC
                LIMIT ?
                """,
                (rs, _) -> new BurstIp(
                        rs.getString("client_ip"),
                        rs.getLong("max_per_minute"),
                        rs.getLong("total")),
                from.toString(), to.toString(), limit);
    }

}
