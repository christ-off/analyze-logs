package com.example.analyzelog.service;

import com.example.analyzelog.config.NoiseFilterProperties;
import com.example.analyzelog.config.RefererFilterProperties;
import com.example.analyzelog.config.RefererNormalizerProperties;
import com.example.analyzelog.config.UriStemFilterProperties;
import com.example.analyzelog.config.UriStemGroupProperties;
import com.example.analyzelog.model.CountryResultTypeCount;
import com.example.analyzelog.model.DailyResultTypeCount;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
            "edge_response_result_type NOT IN (" +
            "'Error','FunctionGeneratedResponse','FunctionExecutionError','FunctionThrottledError')";
    private static final String RESULT_TYPE_GROUP_EXPR =
            "CASE WHEN edge_response_result_type IN " +
            "('FunctionGeneratedResponse','FunctionExecutionError','FunctionThrottledError') " +
            "THEN 'Function' ELSE edge_response_result_type END";
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
    private static final String RESULT_TYPE_SUMS = """
            SUM(CASE WHEN edge_response_result_type = 'Hit'    THEN 1 ELSE 0 END) as hit,
            SUM(CASE WHEN edge_response_result_type = 'Miss'   THEN 1 ELSE 0 END) as miss,
            SUM(CASE WHEN edge_response_result_type IN (
                    'FunctionGeneratedResponse',
                    'FunctionExecutionError',
                    'FunctionThrottledError')                  THEN 1 ELSE 0 END) as function,
            SUM(CASE WHEN edge_response_result_type = 'Error'    THEN 1 ELSE 0 END) as error\
            """;
    private final String sqlUriByResultType;
    private static final String SQL_URI_RESULT_TYPE_GROUP_ORDER = """
            GROUP BY name
            ORDER BY (hit + miss + function + error) DESC
            LIMIT ?
            """;
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
    private final List<RefererNormalizerProperties.Rule> normalizerRules;
    private final List<NoiseFilterProperties.Rule> noiseRules;
    private final Map<String, List<String>> groupPatterns;

    public DashboardService(JdbcTemplate jdbc, EdgeLocationResolver edgeLocationResolver,
                            UriStemFilterProperties uriStemFilterProperties,
                            RefererFilterProperties refererFilterProperties,
                            RefererNormalizerProperties refererNormalizerProperties,
                            UriStemGroupProperties uriStemGroupProperties,
                            NoiseFilterProperties noiseFilterProperties) {
        this.jdbc = jdbc;
        this.edgeLocationResolver = edgeLocationResolver;
        this.excludedExtensions = uriStemFilterProperties.excludedExtensions();
        this.selfReferers = refererFilterProperties.selfReferers();
        this.normalizerRules = refererNormalizerProperties.rules();
        this.noiseRules    = noiseFilterProperties.rules();
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
               " WHERE ua_group IN ('AI Bots','Search Bots','Other Bots','Apps'))";
    }

    private String humanTrafficExclusionClause() {
        return Stream.of(botExclusionClause(), noiseExclusionClause(), RESULT_TYPE_EXCLUSION)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(AND_SEPARATOR));
    }

    private String noiseExclusionClause() {
        return noiseRules.stream()
                .map(_ -> "NOT (ua_name = ? AND uri_stem = ?)")
                .collect(Collectors.joining(AND_SEPARATOR));
    }

    private void addNoiseExclusionArgs(List<Object> args) {
        noiseRules.forEach(r -> { args.add(r.uaName()); args.add(r.uriStem()); });
    }

    public List<NameCount> uaGroupCounts(Instant from, Instant to, boolean excludeBots) {
        var args = new ArrayList<>();
        args.add(from.toString());
        args.add(to.toString());

        String botFilter = "";
        if (excludeBots) {
            String noiseFilter = noiseRules.stream()
                    .map(_ -> "  AND NOT (c.ua_name = ? AND c.uri_stem = ?)\n")
                    .collect(Collectors.joining());
            botFilter = "  AND s.ua_group NOT IN ('AI Bots','Search Bots','Other Bots','Apps')\n" +
                        "  AND c.edge_response_result_type NOT IN (" +
                        "'Error','FunctionGeneratedResponse','FunctionExecutionError','FunctionThrottledError')\n" +
                        noiseFilter;
            addNoiseExclusionArgs(args);
        }

        String sql = "SELECT s.ua_group AS name, COUNT(*) AS count\n" +
                     "FROM cloudfront_logs c\n" +
                     "INNER JOIN static_ua s ON c.ua_name = s.ua_name\n" +
                     "WHERE c.timestamp BETWEEN ? AND ?\n" +
                     botFilter +
                     "GROUP BY s.ua_group\n" +
                     "ORDER BY count DESC";
        return jdbc.query(sql, NAME_COUNT_MAPPER, args.toArray());
    }

    public List<NameResultTypeCount> topUserAgentsByResultType(Instant from, Instant to, int limit, boolean excludeBots) {
        String exclusion = excludeBots ? andClause(humanTrafficExclusionClause()) : "";
        String sql = SQL_SELECT_UA_NAME + RESULT_TYPE_SUMS + "\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                exclusion +
                "GROUP BY ua_name\n" +
                "ORDER BY (hit + miss + function + error) DESC\n" +
                "LIMIT ?\n";
        var args = new ArrayList<>();
        args.add(from.toString());
        args.add(to.toString());
        if (excludeBots) { addNoiseExclusionArgs(args); }
        args.add(limit);
        return jdbc.query(sql, NAME_RESULT_TYPE_COUNT_MAPPER, args.toArray());
    }

    public List<CountryResultTypeCount> topCountriesByResultType(Instant from, Instant to, int limit, boolean excludeBots) {
        String exclusion = excludeBots ? andClause(humanTrafficExclusionClause()) : "";
        String sql = "SELECT country as code,\n" + RESULT_TYPE_SUMS + "\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                "  AND country IS NOT NULL\n" +
                exclusion +
                "GROUP BY country\n" +
                "ORDER BY (hit + miss + function + error) DESC\n" +
                "LIMIT ?\n";
        var args = new ArrayList<>();
        args.add(from.toString());
        args.add(to.toString());
        if (excludeBots) { addNoiseExclusionArgs(args); }
        args.add(limit);
        return jdbc.query(sql, COUNTRY_RESULT_TYPE_COUNT_MAPPER, args.toArray());
    }

    public List<NameResultTypeCount> countryTopUserAgentsByResultType(String countryCode, Instant from, Instant to, int limit, boolean excludeBots) {
        String exclusion = excludeBots ? andClause(humanTrafficExclusionClause()) : "";
        String sql = SQL_SELECT_UA_NAME + RESULT_TYPE_SUMS + "\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                "  AND country = ?\n" +
                exclusion +
                "GROUP BY ua_name\n" +
                "ORDER BY (hit + miss + function + error) DESC\n" +
                "LIMIT ?\n";
        var args = new ArrayList<>();
        args.add(from.toString());
        args.add(to.toString());
        args.add(countryCode);
        if (excludeBots) { addNoiseExclusionArgs(args); }
        args.add(limit);
        return jdbc.query(sql, NAME_RESULT_TYPE_COUNT_MAPPER, args.toArray());
    }

    public List<NameCount> countryResultTypes(String countryCode, Instant from, Instant to, boolean excludeBots) {
        String exclusion = excludeBots ? andClause(humanTrafficExclusionClause()) : "";
        var args = new ArrayList<>();
        args.add(from.toString());
        args.add(to.toString());
        args.add(countryCode);
        if (excludeBots) { addNoiseExclusionArgs(args); }
        String sql = "SELECT " + RESULT_TYPE_GROUP_EXPR + " as name, COUNT(*) as count\n"
                + "FROM cloudfront_logs\n"
                + "WHERE timestamp BETWEEN ? AND ?\n"
                + "  AND country = ?\n"
                + exclusion
                + "GROUP BY name\n"
                + "ORDER BY count DESC\n";
        return jdbc.query(sql, NAME_COUNT_MAPPER, args.toArray());
    }

    public List<NameResultTypeCount> countryUrlsByResultType(String countryCode, Instant from, Instant to, int limit, boolean excludeBots) {
        return urlsByResultType("country = ?", List.of(from.toString(), to.toString(), countryCode), limit, excludeBots);
    }

    public List<DailyResultTypeCount> countryRequestsPerDay(String countryCode, Instant from, Instant to, boolean excludeBots) {
        String exclusion = excludeBots ? andClause(humanTrafficExclusionClause()) : "";
        var args = new ArrayList<>();
        args.add(from.toString());
        args.add(to.toString());
        args.add(countryCode);
        if (excludeBots) { addNoiseExclusionArgs(args); }
        return queryDailyByResultType(SQL_DAILY_SELECT + "  AND country = ?\n" + exclusion + SQL_DAILY_GROUP_ORDER,
                args.toArray());
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
        if (excludeBots) { addNoiseExclusionArgs(args); }
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
                andClause(selfExclusionClause) +
                andClause(botClause) +
                "GROUP BY referer\n" +
                "ORDER BY count DESC\n";

        var args = new ArrayList<>();
        args.add(from.toString());
        args.add(to.toString());
        exclusionPatterns.forEach(args::add);
        if (excludeBots) { addNoiseExclusionArgs(args); }

        List<NameCount> raw = jdbc.query(sql, NAME_COUNT_MAPPER, args.toArray());

        return aggregateByLabel(raw, this::normalizeReferer).stream()
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
            if (host == null) return referer;
            String h = host.startsWith("www.") ? host.substring(4) : host;
            for (RefererNormalizerProperties.Rule rule : normalizerRules) {
                if ((rule.domain() != null && h.equals(rule.domain()))
                        || (rule.domainStartsWith() != null && h.startsWith(rule.domainStartsWith()))
                        || (rule.domainEndsWith() != null && h.endsWith(rule.domainEndsWith()))) {
                    return rule.label();
                }
            }
        } catch (IllegalArgumentException _) {
            // malformed URI — return as-is
        }
        return referer;
    }

    public List<NameResultTypeCount> uaRawUserAgents(String uaName, Instant from, Instant to, boolean excludeBots) {
        String exclusion = excludeBots ? andClause(RESULT_TYPE_EXCLUSION) : "";
        return jdbc.query("SELECT user_agent as name,\n" + RESULT_TYPE_SUMS + "\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                "  AND ua_name = ?\n" +
                exclusion +
                "GROUP BY user_agent\n" +
                "ORDER BY (hit + miss + function + error) DESC\n",
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
        var args = new ArrayList<>();
        args.add(from.toString());
        args.add(to.toString());
        if (excludeBots) { addNoiseExclusionArgs(args); }
        return queryDailyByResultType(sql, args.toArray());
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
        if (excludeBots) { addNoiseExclusionArgs(args); }
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
                "ORDER BY (hit + miss + function + error) DESC\n" +
                "LIMIT ?\n";
        var args = new ArrayList<>();
        args.add(from.toString());
        args.add(to.toString());
        args.addAll(entry.getValue());
        if (excludeBots) { addNoiseExclusionArgs(args); }
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
                "GROUP BY ua_name\n" +
                "ORDER BY (hit + miss + function + error) DESC\n" +
                "LIMIT ?\n";
        var args = new ArrayList<>();
        args.add(from.toString());
        args.add(to.toString());
        args.addAll(entry.getValue());
        if (excludeBots) { addNoiseExclusionArgs(args); }
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
        if (excludeBots) { addNoiseExclusionArgs(args); }
        return queryDailyByResultType(sql, args.toArray());
    }

    private static List<NameCount> aggregateByLabel(List<NameCount> raw, UnaryOperator<String> labeler) {
        Map<String, Long> totals = new LinkedHashMap<>();
        for (NameCount entry : raw) {
            totals.merge(labeler.apply(entry.name()), entry.count(), Long::sum);
        }
        return totals.entrySet().stream()
                .map(e -> new NameCount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(NameCount::count).reversed())
                .toList();
    }
}
