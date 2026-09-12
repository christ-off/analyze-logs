package com.example.analyzelog.service;

import com.example.analyzelog.config.RefererFilterProperties;
import com.example.analyzelog.config.UriStemFilterProperties;
import com.example.analyzelog.config.UriStemGroupProperties;
import com.example.analyzelog.model.BotUaRequest;
import com.example.analyzelog.model.CountryResultTypeCount;
import com.example.analyzelog.model.DailyNameCount;
import com.example.analyzelog.model.DailyResultTypeCount;
import com.example.analyzelog.model.FakeBrowserUa;
import com.example.analyzelog.model.HumanTrafficStats;
import com.example.analyzelog.model.IdentityShift;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.model.NameHumanTrafficStats;
import com.example.analyzelog.model.NameResultTypeCount;
import com.example.analyzelog.model.SiteConfigFetcher;
import com.example.analyzelog.model.SocialNetworkRequest;
import com.example.analyzelog.util.TimestampFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("java:S2077") // dynamic SQL parts are static constants or parameterized — no user input is concatenated
@Service
public class DashboardService {

    private static final String COUNT_FIELD = "count";
    private static final String FIELD_FUNCTION = "function";
    private static final String FIELD_ERROR = "error";
    private static final String AND_SEPARATOR = " AND ";
    private static final String SQL_AND_INDENT = "  AND ";
    private static final String COUNTRY_FILTER = "country = ?";
    private static final String UA_NAME_FILTER = "ua_name = ?";
    // Shared by any single-browser dashboard filtering on "ua_name LIKE '<Browser> / %'".
    private static final String BROWSER_UA_FILTER = "ua_name LIKE ?";
    // Every Chrome ua_name variant (desktop and mobile) shares this "Chrome / <OS>" prefix —
    // the Chrome dashboard aggregates across all of them regardless of OS.
    private static final String CHROME_UA_PATTERN = "Chrome / %";
    // Every Edge ua_name variant (desktop and mobile) shares this "Edge / <OS>" prefix —
    // the Edge dashboard aggregates across all of them regardless of OS.
    private static final String EDGE_UA_PATTERN = "Edge / %";
    private static final String SQL_SELECT_UA_NAME = "SELECT ua_name as name,\n";
    private static final String SQL_SELECT_COUNTRY = "SELECT country as code,\n";
    private static final int UA_COUNTRIES_LIMIT = 10;
    private static final String RESULT_TYPE_EXCLUSION =
            "edge_response_result_type NOT IN ('Error'," + ResultTypeSql.FUNCTION_TYPE_LIST + ")";
    private static final String RESULT_TYPE_GROUP_EXPR =
            "CASE WHEN edge_response_result_type IN (" + ResultTypeSql.FUNCTION_TYPE_LIST + ") " +
            "THEN 'Filtered' ELSE edge_response_result_type END";
    private static final RowMapper<BotUaRequest> BOT_UA_REQUEST_MAPPER = (rs, i) -> {
        String iso = rs.getString("country");
        String countryName = resolveCountryDisplayOrNull(iso);
        if (countryName == null) countryName = "-";
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
    private static final RowMapper<SiteConfigFetcher> SITE_CONFIG_FETCHER_MAPPER =
            (rs, _) -> new SiteConfigFetcher(
                    rs.getString("name"),
                    rs.getLong("hit"), rs.getLong("miss"),
                    rs.getLong(FIELD_FUNCTION), rs.getLong(FIELD_ERROR),
                    rs.getLong("other_requests"));
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
    private static final RowMapper<DailyNameCount> DAILY_NAME_COUNT_MAPPER =
            (rs, _) -> new DailyNameCount(LocalDate.parse(rs.getString("day")), rs.getString("name"), rs.getLong(COUNT_FIELD));
    private static final String URI_STEM_EXCLUSION_PREDICATE = "uri_stem NOT LIKE ?";
    private static final String RESULT_TYPE_SUMS = ResultTypeSql.RESULT_TYPE_SUMS;

    private static final String LIMIT_PARAM = "LIMIT ?\n";
    // Files real browsers never request on their own — a site owner may or may not
    // even publish some of these, so a hit is still a strong non-browser signal.
    private static final String SITE_CONFIG_PATHS_SQL_LIST =
            "'/robots.txt','/ads.txt','/sitemap.xml','/humans.txt','/security.txt'," +
            "'/.well-known/security.txt','/browserconfig.xml','/opensearch.xml'";
    // Only Hit/Miss responses count as "Probable human" evidence — Error, RefreshHit and
    // FunctionGeneratedResponse rows (scanners, edge retries) must not qualify a pair.
    private static final String HUMAN_EVIDENCE_RESULT_TYPES = "edge_response_result_type IN ('Hit','Miss')";
    // Evidence of a real browser fetching a rendered page: it also loaded the site stylesheet,
    // which only a real browser rendering the page requests — bots/scanners never fetch it.
    private static final String HUMAN_EVIDENCE_EXT_PREDICATE = "uri_stem = '/css/main.css'";
    // Any pair (client_ip, user_agent) requesting one of these is classified as the 'Feeds' category.
    private static final String FEED_URI_LIST = "'/feed.xml','/rss.xml'";
    // Pair classification used both to label rows (trafficCategories) and to filter rows
    // belonging to a given category (categoryPairFilter) — kept as one expression so the two stay in sync.
    // Built per-instance (rather than a static constant) because the 'Security' branch depends on the
    // configured uri-stem-groups flagged security: true.
    private static final String CATEGORY_CASE_EXPR_TEMPLATE = """
            CASE
                WHEN MAX(CASE WHEN uri_stem IN (%s) THEN 1 ELSE 0 END) = 1
                    THEN 'Feeds'
                WHEN MAX(CASE WHEN %s THEN 1 ELSE 0 END) = 1
                    THEN 'Security'
                WHEN MAX(CASE WHEN uri_stem LIKE '%%/' AND %s THEN 1 ELSE 0 END) = 1
                 AND MAX(CASE WHEN %s AND %s THEN 1 ELSE 0 END) = 1
                    THEN 'Probable human'
                WHEN MAX(CASE WHEN uri_stem = '/robots.txt' THEN 1 ELSE 0 END) = 1
                    THEN 'Declared bots'
                ELSE 'Other'
            END""";

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
    private final ReloadableRefererService refererService;
    private final Map<String, List<String>> groupPatterns;
    private final List<String> securityGroupNames;
    private final String categoryCaseExpr;
    private final String uriStemExclusionClause;
    private final List<String> extensionArgs;
    private final List<String> selfExclusionPatterns;
    private final String selfExclusionClause;

    public DashboardService(JdbcTemplate jdbc, EdgeLocationResolver edgeLocationResolver,
                            UriStemFilterProperties uriStemFilterProperties,
                            RefererFilterProperties refererFilterProperties,
                            ReloadableRefererService refererService,
                            UriStemGroupProperties uriStemGroupProperties) {
        this.jdbc = jdbc;
        this.edgeLocationResolver = edgeLocationResolver;
        List<String> excludedExtensions = uriStemFilterProperties.excludedExtensions();
        List<String> selfReferers = refererFilterProperties.selfReferers();
        this.refererService = refererService;
        this.groupPatterns = uriStemGroupProperties.groups().stream()
                .collect(Collectors.toMap(
                        UriStemGroupProperties.Group::name,
                        UriStemGroupProperties.Group::patterns));
        this.securityGroupNames = uriStemGroupProperties.groups().stream()
                .filter(UriStemGroupProperties.Group::security)
                .map(UriStemGroupProperties.Group::name)
                .toList();
        this.categoryCaseExpr = CATEGORY_CASE_EXPR_TEMPLATE.formatted(FEED_URI_LIST, securityUriStemWhenClause(),
                HUMAN_EVIDENCE_RESULT_TYPES, HUMAN_EVIDENCE_EXT_PREDICATE, HUMAN_EVIDENCE_RESULT_TYPES);
        this.sqlUriByResultType = "SELECT \n" +
                buildUriStemNameCase(uriStemGroupProperties.groups()) +
                RESULT_TYPE_SUMS + "\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n";
        this.uriStemExclusionClause = excludedExtensions.stream()
                .map(_ -> URI_STEM_EXCLUSION_PREDICATE)
                .collect(Collectors.joining(AND_SEPARATOR));
        this.extensionArgs = excludedExtensions.stream()
                .map(ext -> "%." + ext.replaceFirst("^\\.", ""))
                .toList();
        List<String> selfPatterns = selfReferers.stream()
                .flatMap(prefix -> buildSelfExclusionPatterns(prefix).stream())
                .toList();
        this.selfExclusionPatterns = selfPatterns;
        this.selfExclusionClause = selfPatterns.stream()
                .map(_ -> "referer NOT LIKE ?")
                .collect(Collectors.joining(AND_SEPARATOR));
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

    // Inline (not parameterized) equivalent of securityPredicate(), for embedding into CATEGORY_CASE_EXPR_TEMPLATE
    // alongside other literal patterns (FEED_URI_LIST) — same trusted-config style as buildUriStemNameCase().
    private String securityUriStemWhenClause() {
        return securityGroupNames.stream()
                .flatMap(name -> groupPatterns.get(name).stream())
                .map(pattern -> "LOWER(uri_stem) LIKE LOWER('" + pattern + "')")
                .collect(Collectors.joining(" OR "));
    }

    // Union of every uri-stem-groups entry flagged security: true — used to filter overall security-scan traffic.
    private Map.Entry<String, List<Object>> securityPredicate() {
        List<String> patterns = securityGroupNames.stream()
                .flatMap(name -> groupPatterns.get(name).stream())
                .toList();
        String pred = patterns.stream()
                .map(_ -> "LOWER(uri_stem) LIKE LOWER(?)")
                .collect(Collectors.joining(" OR ", "(", ")"));
        return Map.entry(pred, List.copyOf(patterns));
    }

    private static String andClause(String clause) {
        return clause.isEmpty() ? "" : SQL_AND_INDENT + clause + "\n";
    }

    private static String resolveCountryDisplay(String iso, String blankFallback) {
        if (iso == null || iso.isBlank()) return blankFallback;
        String display = Locale.of("", iso).getDisplayCountry(Locale.ENGLISH);
        return (display != null && !display.isBlank()) ? display : iso;
    }

    private static String resolveCountryLabel(String iso) {
        return resolveCountryDisplay(iso, iso);
    }

    private static String resolveCountryDisplayOrNull(String iso) {
        return resolveCountryDisplay(iso, null);
    }


    public List<NameCount> uaGroupCounts(Instant from, Instant to) {
        String sql = "SELECT s.ua_group AS name, COUNT(*) AS count\n" +
                     "FROM cloudfront_logs c\n" +
                     "INNER JOIN static_ua s ON c.ua_name = s.ua_name\n" +
                     "WHERE c.timestamp BETWEEN ? AND ?\n" +
                     "GROUP BY s.ua_group\n" +
                     "ORDER BY count DESC";
        return jdbc.query(sql, NAME_COUNT_MAPPER, TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to));
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
        return jdbc.query(sql, NAME_RESULT_TYPE_COUNT_MAPPER, TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), limit);
    }

    private List<NameResultTypeCount> uaResultTypesByFilter(String additionalFilter, List<Object> extraArgs,
                                                              Instant from, Instant to, int limit) {
        String sql = SQL_SELECT_UA_NAME + RESULT_TYPE_SUMS + "\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                andClause(additionalFilter) +
                GROUP_BY_UA_NAME +
                ResultTypeSql.ORDER_BY_TOTAL_DESC +
                LIMIT_PARAM;
        var args = new ArrayList<>();
        args.add(TimestampFormat.sqlValue(from));
        args.add(TimestampFormat.sqlValue(to));
        args.addAll(extraArgs);
        args.add(limit);
        return jdbc.query(sql, NAME_RESULT_TYPE_COUNT_MAPPER, args.toArray());
    }

    public List<NameResultTypeCount> topUserAgentsByResultType(Instant from, Instant to, int limit) {
        return uaResultTypesByFilter("", List.of(), from, to, limit);
    }

    private List<CountryResultTypeCount> countryResultTypesByFilter(String additionalFilter, List<Object> extraArgs,
                                                                      Instant from, Instant to, int limit) {
        String sql = SQL_SELECT_COUNTRY + RESULT_TYPE_SUMS + "\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                "  AND country IS NOT NULL\n" +
                andClause(additionalFilter) +
                "GROUP BY country\n" +
                ResultTypeSql.ORDER_BY_TOTAL_DESC +
                LIMIT_PARAM;
        var args = new ArrayList<>();
        args.add(TimestampFormat.sqlValue(from));
        args.add(TimestampFormat.sqlValue(to));
        args.addAll(extraArgs);
        args.add(limit);
        return jdbc.query(sql, COUNTRY_RESULT_TYPE_COUNT_MAPPER, args.toArray());
    }

    public List<CountryResultTypeCount> topCountriesByResultType(Instant from, Instant to, int limit) {
        return countryResultTypesByFilter("", List.of(), from, to, limit);
    }

    public List<CountryResultTypeCount> topCountriesByFilteredRatio(Instant from, Instant to, int limit) {
        String sql = SQL_SELECT_COUNTRY + RESULT_TYPE_SUMS + "\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                "  AND country IS NOT NULL\n" +
                "GROUP BY country\n" +
                "HAVING function > 0\n" +
                "ORDER BY CASE WHEN (hit + miss) = 0 THEN CAST(function AS REAL)\n" +
                "              ELSE CAST(function AS REAL) / (hit + miss) END DESC\n" +
                LIMIT_PARAM;
        return jdbc.query(sql, COUNTRY_RESULT_TYPE_COUNT_MAPPER, TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), limit);
    }

    public List<NameResultTypeCount> countryTopUserAgentsByResultType(String countryCode, Instant from, Instant to, int limit) {
        return uaResultTypesByFilter(COUNTRY_FILTER, List.of(countryCode), from, to, limit);
    }

    public List<NameCount> countryResultTypes(String countryCode, Instant from, Instant to) {
        return queryResultTypesByFilter(COUNTRY_FILTER, countryCode, from, to);
    }

    public List<NameResultTypeCount> countryUrlsByResultType(String countryCode, Instant from, Instant to, int limit) {
        return urlsByResultType(COUNTRY_FILTER, List.of(TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), countryCode), limit);
    }

    public List<DailyResultTypeCount> countryRequestsPerDay(String countryCode, Instant from, Instant to) {
        return queryDailyByResultType(SQL_DAILY_SELECT + "  AND country = ?\n" + SQL_DAILY_GROUP_ORDER,
                TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), countryCode);
    }

    public List<NameResultTypeCount> topUrlsByResultType(Instant from, Instant to, int limit) {
        return urlsByResultType("", List.of(TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to)), limit);
    }

    private List<NameResultTypeCount> urlsByResultType(String additionalFilter, List<Object> baseArgs, int limit) {
        String sql = sqlUriByResultType
                + andClause(additionalFilter)
                + andClause(uriStemExclusionClause)
                + SQL_URI_RESULT_TYPE_GROUP_ORDER;

        var args = new ArrayList<>(baseArgs);
        args.addAll(extensionArgs);
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
                TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), limit);
    }

    public List<NameCount> platformCounts(Instant from, Instant to) {
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
                GROUP BY name
                ORDER BY count DESC
                """;
        return jdbc.query(sql, NAME_COUNT_MAPPER, TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to));
    }

    public List<NameCount> topReferers(Instant from, Instant to, int limit) {
        String sql = "SELECT referer as name, COUNT(*) as count\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                "  AND referer IS NOT NULL\n" +
                "  AND " + RESULT_TYPE_EXCLUSION + "\n" +
                andClause(selfExclusionClause) +
                "GROUP BY referer\n" +
                "ORDER BY count DESC\n";

        var args = new ArrayList<>();
        args.add(TimestampFormat.sqlValue(from));
        args.add(TimestampFormat.sqlValue(to));
        args.addAll(selfExclusionPatterns);

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

    private static List<String> buildSelfExclusionPatterns(String configuredReferer) {
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

    private List<NameResultTypeCount> rawUserAgentsByFilter(String filterClause, Object filterArg, Instant from, Instant to) {
        return jdbc.query("SELECT user_agent as name,\n" + RESULT_TYPE_SUMS + "\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                "  AND " + filterClause + "\n" +
                "GROUP BY user_agent\n" +
                ResultTypeSql.ORDER_BY_TOTAL_DESC,
                NAME_RESULT_TYPE_COUNT_MAPPER,
                TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), filterArg);
    }

    public List<NameResultTypeCount> uaRawUserAgents(String uaName, Instant from, Instant to) {
        return rawUserAgentsByFilter(UA_NAME_FILTER, uaName, from, to);
    }

    // Every raw Chrome user_agent string, whatever the OS (ua_name LIKE 'Chrome / %').
    public List<NameResultTypeCount> chromeRawUserAgents(Instant from, Instant to) {
        return rawUserAgentsByFilter(BROWSER_UA_FILTER, CHROME_UA_PATTERN, from, to);
    }

    // Every raw Edge user_agent string, whatever the OS (ua_name LIKE 'Edge / %').
    public List<NameResultTypeCount> edgeRawUserAgents(Instant from, Instant to) {
        return rawUserAgentsByFilter(BROWSER_UA_FILTER, EDGE_UA_PATTERN, from, to);
    }

    // Per raw user_agent string, proportion of requests whose (client_ip, user_agent) pair
    // classifies as "Probable human" — same categoryCaseExpr used by humanTrafficStats()/trafficCategories(),
    // just grouped per user_agent instead of aggregated to one total.
    private List<NameHumanTrafficStats> humanTrafficByUserAgent(String filterClause, Object filterArg, Instant from, Instant to) {
        String sql = """
                WITH pair_class AS (
                    SELECT client_ip, user_agent,
                        %s AS category
                    FROM cloudfront_logs
                    WHERE timestamp BETWEEN ? AND ?
                    GROUP BY client_ip, user_agent
                )
                SELECT c.user_agent AS name,
                       SUM(CASE WHEN pc.category = 'Probable human' THEN 1 ELSE 0 END) AS human,
                       COUNT(*) AS total
                FROM cloudfront_logs c
                JOIN pair_class pc ON c.client_ip = pc.client_ip AND c.user_agent = pc.user_agent
                WHERE c.timestamp BETWEEN ? AND ?
                  AND c.%s
                GROUP BY c.user_agent
                """.formatted(categoryCaseExpr, filterClause);
        String fromSql = TimestampFormat.sqlValue(from);
        String toSql = TimestampFormat.sqlValue(to);
        return jdbc.query(sql,
                (rs, _) -> new NameHumanTrafficStats(rs.getString("name"), rs.getLong("human"), rs.getLong("total")),
                fromSql, toSql, fromSql, toSql, filterArg);
    }

    public List<NameHumanTrafficStats> uaHumanTrafficByUserAgent(String uaName, Instant from, Instant to) {
        return humanTrafficByUserAgent(UA_NAME_FILTER, uaName, from, to);
    }

    // Every raw Chrome user_agent string, whatever the OS (ua_name LIKE 'Chrome / %').
    public List<NameHumanTrafficStats> chromeHumanTraffic(Instant from, Instant to) {
        return humanTrafficByUserAgent(BROWSER_UA_FILTER, CHROME_UA_PATTERN, from, to);
    }

    // Every raw Edge user_agent string, whatever the OS (ua_name LIKE 'Edge / %').
    public List<NameHumanTrafficStats> edgeHumanTraffic(Instant from, Instant to) {
        return humanTrafficByUserAgent(BROWSER_UA_FILTER, EDGE_UA_PATTERN, from, to);
    }

    public List<NameCount> uaResultTypes(String uaName, Instant from, Instant to) {
        return queryResultTypesByFilter(UA_NAME_FILTER, uaName, from, to);
    }

    public List<NameCount> chromeResultTypes(Instant from, Instant to) {
        return queryResultTypesByFilter(BROWSER_UA_FILTER, CHROME_UA_PATTERN, from, to);
    }

    public List<NameCount> edgeResultTypes(Instant from, Instant to) {
        return queryResultTypesByFilter(BROWSER_UA_FILTER, EDGE_UA_PATTERN, from, to);
    }

    private List<NameCount> countriesByFilter(String filterClause, Object filterArg, Instant from, Instant to) {
        return jdbc.query("SELECT country as name, COUNT(*) as count\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                "  AND " + filterClause + "\n" +
                "GROUP BY country\n" +
                "ORDER BY count DESC\n" +
                "LIMIT " + UA_COUNTRIES_LIMIT + "\n",
                (rs, _) -> new NameCount(resolveCountryLabel(rs.getString("name")), rs.getLong(COUNT_FIELD)),
                TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), filterArg);
    }

    public List<NameCount> uaCountries(String uaName, Instant from, Instant to) {
        return countriesByFilter(UA_NAME_FILTER, uaName, from, to);
    }

    public List<NameCount> chromeCountries(Instant from, Instant to) {
        return countriesByFilter(BROWSER_UA_FILTER, CHROME_UA_PATTERN, from, to);
    }

    public List<NameCount> edgeCountries(Instant from, Instant to) {
        return countriesByFilter(BROWSER_UA_FILTER, EDGE_UA_PATTERN, from, to);
    }

    public List<NameResultTypeCount> uaUrlsByResultType(String uaName, Instant from, Instant to, int limit) {
        return urlsByResultType(UA_NAME_FILTER, List.of(TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), uaName), limit);
    }

    public List<NameResultTypeCount> chromeUrlsByResultType(Instant from, Instant to, int limit) {
        return urlsByResultType(BROWSER_UA_FILTER, List.of(TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), CHROME_UA_PATTERN), limit);
    }

    public List<NameResultTypeCount> edgeUrlsByResultType(Instant from, Instant to, int limit) {
        return urlsByResultType(BROWSER_UA_FILTER, List.of(TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), EDGE_UA_PATTERN), limit);
    }

    private List<DailyResultTypeCount> requestsPerDayByFilter(String filterClause, Object filterArg, Instant from, Instant to) {
        return queryDailyByResultType(SQL_DAILY_SELECT + "  AND " + filterClause + "\n" + SQL_DAILY_GROUP_ORDER,
                TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), filterArg);
    }

    public List<DailyResultTypeCount> uaRequestsPerDay(String uaName, Instant from, Instant to) {
        return requestsPerDayByFilter(UA_NAME_FILTER, uaName, from, to);
    }

    public List<DailyResultTypeCount> chromeRequestsPerDay(Instant from, Instant to) {
        return requestsPerDayByFilter(BROWSER_UA_FILTER, CHROME_UA_PATTERN, from, to);
    }

    public List<DailyResultTypeCount> edgeRequestsPerDay(Instant from, Instant to) {
        return requestsPerDayByFilter(BROWSER_UA_FILTER, EDGE_UA_PATTERN, from, to);
    }

    public List<DailyResultTypeCount> requestsPerDay(Instant from, Instant to) {
        String sql = SQL_DAILY_SELECT + SQL_DAILY_GROUP_ORDER;
        return queryDailyByResultType(sql, TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to));
    }

    private List<DailyResultTypeCount> queryDailyByResultType(String sql, Object... args) {
        return jdbc.query(sql, DAILY_RESULT_TYPE_COUNT_MAPPER, args);
    }

    // filterClause is always a trusted Java constant, never user input
    private List<NameCount> queryResultTypesByFilter(String filterClause, Object value, Instant from, Instant to) {
        String sql = "SELECT " + RESULT_TYPE_GROUP_EXPR + " as name, COUNT(*) as count\n"
                + "FROM cloudfront_logs\n"
                + "WHERE timestamp BETWEEN ? AND ?\n"
                + "  AND " + filterClause + "\n"
                + "GROUP BY name\n"
                + "ORDER BY count DESC\n";
        return jdbc.query(sql, NAME_COUNT_MAPPER, TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), value);
    }

    public List<NameResultTypeCount> urlMatchingUriStems(String urlName, Instant from, Instant to) {
        var entry = uriStemPredicate(urlName);
        String sql = "SELECT uri_stem as name,\n" +
                RESULT_TYPE_SUMS + "\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                "  AND " + entry.getKey() + "\n" +
                "GROUP BY uri_stem\n" +
                ResultTypeSql.ORDER_BY_TOTAL_DESC;
        var args = new ArrayList<>();
        args.add(TimestampFormat.sqlValue(from));
        args.add(TimestampFormat.sqlValue(to));
        args.addAll(entry.getValue());
        return jdbc.query(sql, NAME_RESULT_TYPE_COUNT_MAPPER, args.toArray());
    }

    public List<CountryResultTypeCount> urlTopCountriesByResultType(String urlName, Instant from, Instant to, int limit) {
        var entry = uriStemPredicate(urlName);
        return countryResultTypesByFilter(entry.getKey(), entry.getValue(), from, to, limit);
    }

    public List<NameResultTypeCount> urlTopUserAgentsByResultType(String urlName, Instant from, Instant to, int limit) {
        var entry = uriStemPredicate(urlName);
        return uaResultTypesByFilter(entry.getKey(), entry.getValue(), from, to, limit);
    }

    public List<DailyResultTypeCount> urlRequestsPerDay(String urlName, Instant from, Instant to) {
        var entry = uriStemPredicate(urlName);
        String sql = SQL_DAILY_SELECT + SQL_AND_INDENT + entry.getKey() + "\n" + SQL_DAILY_GROUP_ORDER;
        var args = new ArrayList<>();
        args.add(TimestampFormat.sqlValue(from));
        args.add(TimestampFormat.sqlValue(to));
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
                TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), limit);
    }

    public List<NameResultTypeCount> refererTopUrlsByResultType(String refererLabel, Instant from, Instant to, int limit) {
        return urlsByResultType("referer LIKE ?", List.of(TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), "%" + refererLabel + "%"), limit);
    }

    public List<DailyResultTypeCount> refererRequestsPerDay(String refererLabel, Instant from, Instant to) {
        String sql = SQL_DAILY_SELECT +
                "  AND referer LIKE ?\n" +
                SQL_DAILY_GROUP_ORDER;
        return queryDailyByResultType(sql, TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), "%" + refererLabel + "%");
    }

    public List<NameResultTypeCount> trafficCategories(String country, Instant from, Instant to) {
        return trafficCategories(COUNTRY_FILTER, List.of(country), from, to);
    }

    public List<NameResultTypeCount> trafficCategories(Instant from, Instant to) {
        return trafficCategories("", List.of(), from, to);
    }

    // package-private, extra filter reserved for later reuse (e.g. "ua_name = ?", "country = ?")
    List<NameResultTypeCount> trafficCategories(String additionalFilter, List<Object> extraArgs,
                                                 Instant from, Instant to) {
        return trafficCategories(additionalFilter, extraArgs, from, to, false);
    }

    // excludeWebp: drop .webp requests from the outer per-request count (they're kept as
    // "Probable human" evidence in the pair_class CTE) — used for the human-traffic proportion,
    // where bulk webp asset downloads shouldn't inflate the request totals.
    private List<NameResultTypeCount> trafficCategories(String additionalFilter, List<Object> extraArgs,
                                                 Instant from, Instant to, boolean excludeWebp) {
        // No outer filtering needed — we count all result types from classified pairs.
        String whereAfterRange = additionalFilter.isEmpty() ? "" : SQL_AND_INDENT + additionalFilter + "\n";
        String outerWebpExclusion = excludeWebp ? "  AND c.uri_stem NOT LIKE '%.webp'\n" : "";

        String sql = """
                WITH pair_class AS (
                    SELECT client_ip, user_agent,
                        %s AS category
                    FROM cloudfront_logs
                    WHERE timestamp BETWEEN ? AND ?
                    %s
                    GROUP BY client_ip, user_agent
                )
                SELECT pc.category AS name,
                       %s
                FROM cloudfront_logs c
                JOIN pair_class pc ON c.client_ip = pc.client_ip AND c.user_agent = pc.user_agent
                WHERE c.timestamp BETWEEN ? AND ?
                %s
                GROUP BY name
                ORDER BY CASE name
                    WHEN 'Probable human' THEN 0
                    WHEN 'Declared bots' THEN 1
                    WHEN 'Feeds' THEN 2
                    WHEN 'Security' THEN 3
                    ELSE 4 END
                """.formatted(
                categoryCaseExpr,
                whereAfterRange,
                ResultTypeSql.resultTypeSums("c"),
                outerWebpExclusion
        );

        String fromSql = TimestampFormat.sqlValue(from);
        String toSql = TimestampFormat.sqlValue(to);
        var args = new ArrayList<Object>(List.of(fromSql, toSql));
        args.addAll(extraArgs);
        args.addAll(List.of(fromSql, toSql));

        return jdbc.query(sql, NAME_RESULT_TYPE_COUNT_MAPPER, args.toArray());
    }

    public List<NameCount> securityTrafficCategories(Instant from, Instant to) {
        var args = new ArrayList<>();
        String sql = buildSecurityUnionSql(securityGroupNames, from, to, args,
                name -> "SELECT '" + name + "' as name, COUNT(*) as count\n"
                        + "FROM cloudfront_logs\n"
                        + "WHERE timestamp BETWEEN ? AND ?\n"
                        + "  AND %s\n");
        return jdbc.query(sql + "ORDER BY count DESC\n", NAME_COUNT_MAPPER, args.toArray());
    }

    public List<DailyNameCount> securityRequestsPerDay(Instant from, Instant to) {
        var args = new ArrayList<>();
        String sql = buildSecurityUnionSql(securityGroupNames, from, to, args,
                name -> "SELECT date(timestamp) as day, '" + name + "' as name, COUNT(*) as count\n"
                        + "FROM cloudfront_logs\n"
                        + "WHERE timestamp BETWEEN ? AND ?\n"
                        + "  AND %s\n"
                        + "GROUP BY day\n");
        return jdbc.query(sql + "ORDER BY day\n", DAILY_NAME_COUNT_MAPPER, args.toArray());
    }

    private String buildSecurityUnionSql(List<String> names, Instant from, Instant to,
                                          List<Object> args,
                                          java.util.function.Function<String, String> selectFmt) {
        return names.stream()
                .map(name -> {
                    var entry = uriStemPredicate(name);
                    args.add(TimestampFormat.sqlValue(from));
                    args.add(TimestampFormat.sqlValue(to));
                    args.addAll(entry.getValue());
                    return selectFmt.apply(name).formatted(entry.getKey());
                })
                .collect(Collectors.joining("UNION ALL\n"));
    }

    public List<CountryResultTypeCount> securityTopCountries(Instant from, Instant to, int limit) {
        var entry = securityPredicate();
        String sql = SQL_SELECT_COUNTRY + ResultTypeSql.RESULT_TYPE_SUMS + "\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                "  AND country IS NOT NULL\n" +
                "  AND " + entry.getKey() + "\n" +
                "GROUP BY country\n" +
                ResultTypeSql.ORDER_BY_TOTAL_DESC +
                LIMIT_PARAM;
        var args = new ArrayList<>();
        args.add(TimestampFormat.sqlValue(from));
        args.add(TimestampFormat.sqlValue(to));
        args.addAll(entry.getValue());
        args.add(limit);
        return jdbc.query(sql, COUNTRY_RESULT_TYPE_COUNT_MAPPER, args.toArray());
    }


    // Matches a row's effective category — same pair classification used in trafficCategories().
    private String categoryPairFilter() {
        return """
                (client_ip, user_agent) IN (
                    SELECT client_ip, user_agent
                    FROM cloudfront_logs
                    WHERE timestamp BETWEEN ? AND ?
                    GROUP BY client_ip, user_agent
                    HAVING %s = ?
                )""".formatted(categoryCaseExpr);
    }

    public List<NameResultTypeCount> categoryUrlsByResultType(String category, Instant from, Instant to, int limit) {
        String fromSql = TimestampFormat.sqlValue(from);
        String toSql = TimestampFormat.sqlValue(to);
        return urlsByResultType(categoryPairFilter(), List.of(fromSql, toSql, fromSql, toSql, category), limit);
    }

    public List<NameResultTypeCount> categoryTopUserAgentsByResultType(String category, Instant from, Instant to, int limit) {
        return uaResultTypesByFilter(categoryPairFilter(),
                List.of(TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), category), from, to, limit);
    }

    // Reuses the "Probable human" (client_ip, user_agent) pair classification, scoped to one UA.
    public HumanTrafficStats humanTrafficStats(String ua, Instant from, Instant to) {
        List<NameResultTypeCount> categories =
                trafficCategories("user_agent = ?", List.of(ua), from, to, true);
        return toHumanTrafficStats(categories);
    }

    // Reuses the "Probable human" (client_ip, user_agent) pair classification, scoped to one country.
    public HumanTrafficStats countryHumanTrafficStats(String country, Instant from, Instant to) {
        List<NameResultTypeCount> categories =
                trafficCategories(COUNTRY_FILTER, List.of(country), from, to, true);
        return toHumanTrafficStats(categories);
    }

    private static HumanTrafficStats toHumanTrafficStats(List<NameResultTypeCount> categories) {
        long total = categories.stream().mapToLong(DashboardService::totalCount).sum();
        long human = categories.stream()
                .filter(c -> "Probable human".equals(c.name()))
                .mapToLong(DashboardService::totalCount)
                .sum();
        return new HumanTrafficStats(human, total);
    }

    private static long totalCount(NameResultTypeCount c) {
        return c.hit() + c.miss() + c.function() + c.error();
    }

    public List<BotUaRequest> requestsByUserAgent(String ua, Instant from, Instant to) {
        String sql = """
                SELECT timestamp, client_ip, uri_stem, country, status,
                       %s as result_type
                FROM cloudfront_logs
                WHERE user_agent = ?
                  AND timestamp >= ? AND timestamp < ?
                ORDER BY timestamp DESC
                """.formatted(RESULT_TYPE_GROUP_EXPR);
        return jdbc.query(sql, BOT_UA_REQUEST_MAPPER, ua, TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to));
    }

    public List<DailyResultTypeCount> requestsPerDayByUserAgent(String ua, Instant from, Instant to) {
        return queryDailyByResultType(SQL_DAILY_SELECT + "  AND user_agent = ?\n" + SQL_DAILY_GROUP_ORDER,
                TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), ua);
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
                TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), limit);
    }

    // Browser-classified UAs requesting site config files — robots.txt, ads.txt, sitemap.xml
    // and other files real browsers never fetch on their own. Also reports how many other
    // (non-config) requests the same UA made, since a real browser that stumbles into one of
    // these paths still browses the rest of the site, while a bot mostly won't.
    public List<SiteConfigFetcher> browserConfigFetches(Instant from, Instant to, int limit) {
        return jdbc.query("""
                SELECT c.user_agent AS name,
                       SUM(CASE WHEN c.uri_stem IN (%1$s) AND c.edge_response_result_type = 'Hit'  THEN 1 ELSE 0 END) AS hit,
                       SUM(CASE WHEN c.uri_stem IN (%1$s) AND c.edge_response_result_type = 'Miss' THEN 1 ELSE 0 END) AS miss,
                       SUM(CASE WHEN c.uri_stem IN (%1$s) AND c.edge_response_result_type IN (%2$s) THEN 1 ELSE 0 END) AS function,
                       SUM(CASE WHEN c.uri_stem IN (%1$s) AND c.edge_response_result_type = 'Error' THEN 1 ELSE 0 END) AS error,
                       SUM(CASE WHEN c.uri_stem NOT IN (%1$s) THEN 1 ELSE 0 END) AS other_requests
                FROM cloudfront_logs c
                INNER JOIN static_ua s ON c.ua_name = s.ua_name
                WHERE s.ua_group = 'Browsers'
                  AND c.timestamp BETWEEN ? AND ?
                GROUP BY c.user_agent
                HAVING (hit + miss + function + error) > 0
                ORDER BY other_requests DESC
                """.formatted(SITE_CONFIG_PATHS_SQL_LIST, ResultTypeSql.FUNCTION_TYPE_LIST)
                + LIMIT_PARAM,
                SITE_CONFIG_FETCHER_MAPPER,
                TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), limit);
    }

    private static final String BOT_UA_GROUPS_FOR_IDENTITY_SHIFT = "'AI Bots','Search Bots','Other Bots'";

    private record IpSeen(String ip, Instant firstSeen, Instant lastSeen) {}

    private static String placeholders(int n) {
        return String.join(",", Collections.nCopies(n, "?"));
    }

    // IPs that presented more than one distinct known-bot identity (ua_name in a bot ua_group)
    // within the range — real crawlers each operate from their own infrastructure and never
    // share an IP, so one IP claiming several of them is UA-spoofed scraping ("face dancing").
    public List<IdentityShift> identityShiftingIps(Instant from, Instant to, int ipLimit, int uaLimit, int urlLimit) {
        List<IpSeen> ips = jdbc.query("""
                SELECT c.client_ip AS ip, MIN(c.timestamp) AS first_seen, MAX(c.timestamp) AS last_seen
                FROM cloudfront_logs c
                INNER JOIN static_ua s ON c.ua_name = s.ua_name
                WHERE c.timestamp BETWEEN ? AND ?
                  AND s.ua_group IN (%s)
                GROUP BY c.client_ip
                HAVING COUNT(DISTINCT c.ua_name) > 1
                ORDER BY last_seen DESC
                LIMIT ?
                """.formatted(BOT_UA_GROUPS_FOR_IDENTITY_SHIFT),
                (rs, _) -> new IpSeen(rs.getString("ip"),
                        Instant.parse(rs.getString("first_seen")), Instant.parse(rs.getString("last_seen"))),
                TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), ipLimit);
        if (ips.isEmpty()) return List.of();

        List<String> ipValues = ips.stream().map(IpSeen::ip).toList();
        String inClause = placeholders(ipValues.size());

        Map<String, List<NameCount>> userAgentsByIp = new LinkedHashMap<>();
        var uaArgs = new ArrayList<>();
        uaArgs.add(TimestampFormat.sqlValue(from));
        uaArgs.add(TimestampFormat.sqlValue(to));
        uaArgs.addAll(ipValues);
        uaArgs.add(uaLimit);
        jdbc.query("""
                SELECT client_ip, name, count FROM (
                    SELECT client_ip, user_agent AS name, COUNT(*) AS count,
                           ROW_NUMBER() OVER (PARTITION BY client_ip ORDER BY COUNT(*) DESC) AS rn
                    FROM cloudfront_logs
                    WHERE timestamp BETWEEN ? AND ? AND client_ip IN (%s)
                    GROUP BY client_ip, user_agent
                )
                WHERE rn <= ?
                ORDER BY client_ip, count DESC
                """.formatted(inClause),
                (RowCallbackHandler) (rs ->
                        userAgentsByIp.computeIfAbsent(rs.getString("client_ip"), _ -> new ArrayList<>())
                                .add(new NameCount(rs.getString("name"), rs.getLong(COUNT_FIELD)))),
                uaArgs.toArray());

        record UrlAgg(String ip, String name, long hit, long miss, long function, long error) {}
        List<UrlAgg> urlAggs = new ArrayList<>();
        var urlArgs = new ArrayList<>();
        urlArgs.add(TimestampFormat.sqlValue(from));
        urlArgs.add(TimestampFormat.sqlValue(to));
        urlArgs.addAll(ipValues);
        urlArgs.add(urlLimit);
        jdbc.query("""
                SELECT client_ip, name, hit, miss, function, error FROM (
                    SELECT *,
                        ROW_NUMBER() OVER (PARTITION BY client_ip ORDER BY (hit + miss + function + error) DESC) AS rn
                    FROM (
                        SELECT client_ip, uri_stem AS name,
                            %s
                        FROM cloudfront_logs
                        WHERE timestamp BETWEEN ? AND ? AND client_ip IN (%s)
                        GROUP BY client_ip, uri_stem
                    )
                )
                WHERE rn <= ?
                ORDER BY client_ip, (hit + miss + function + error) DESC
                """.formatted(RESULT_TYPE_SUMS, inClause),
                (RowCallbackHandler) (rs ->
                        urlAggs.add(new UrlAgg(rs.getString("client_ip"), rs.getString("name"),
                                rs.getLong("hit"), rs.getLong("miss"), rs.getLong(FIELD_FUNCTION), rs.getLong(FIELD_ERROR)))),
                urlArgs.toArray());
        if (urlAggs.isEmpty()) {
            return ips.stream()
                    .map(ip -> new IdentityShift(ip.ip(), ip.firstSeen(), ip.lastSeen(),
                            userAgentsByIp.getOrDefault(ip.ip(), List.of()), List.of()))
                    .toList();
        }

        // Which of the IP's user agents fetched each of the (ip, url) pairs just selected above —
        // scoped to that exact pair list so a prolific IP's thousands of other URLs aren't scanned.
        Map<String, Map<String, List<String>>> userAgentsByIpUrl = new LinkedHashMap<>();
        var pairArgs = new ArrayList<>();
        pairArgs.add(TimestampFormat.sqlValue(from));
        pairArgs.add(TimestampFormat.sqlValue(to));
        for (UrlAgg u : urlAggs) {
            pairArgs.add(u.ip());
            pairArgs.add(u.name());
        }
        String pairPlaceholders = urlAggs.stream().map(_ -> "(?,?)").collect(Collectors.joining(","));
        jdbc.query("""
                SELECT DISTINCT client_ip, uri_stem, user_agent
                FROM cloudfront_logs
                WHERE timestamp BETWEEN ? AND ?
                  AND (client_ip, uri_stem) IN (VALUES %s)
                """.formatted(pairPlaceholders),
                (RowCallbackHandler) (rs ->
                        userAgentsByIpUrl.computeIfAbsent(rs.getString("client_ip"), _ -> new LinkedHashMap<>())
                                .computeIfAbsent(rs.getString("uri_stem"), _ -> new ArrayList<>())
                                .add(rs.getString("user_agent"))),
                pairArgs.toArray());

        Map<String, List<IdentityShift.IdentityShiftUrl>> urlsByIp = new LinkedHashMap<>();
        for (UrlAgg u : urlAggs) {
            List<String> uas = userAgentsByIpUrl.getOrDefault(u.ip(), Map.of()).getOrDefault(u.name(), List.of());
            urlsByIp.computeIfAbsent(u.ip(), _ -> new ArrayList<>())
                    .add(new IdentityShift.IdentityShiftUrl(u.name(), u.hit(), u.miss(), u.function(), u.error(), uas));
        }

        return ips.stream()
                .map(ip -> new IdentityShift(ip.ip(), ip.firstSeen(), ip.lastSeen(),
                        userAgentsByIp.getOrDefault(ip.ip(), List.of()),
                        urlsByIp.getOrDefault(ip.ip(), List.of())))
                .toList();
    }

    // Link-preview crawler UA substrings and click-through referer domains for each known
    // social/messaging network. Order matters: first match wins for requests that could match more than one.
    private record SocialNetworkRule(String label, List<String> userAgentPatterns, List<String> refererDomains) {}

    private static final List<SocialNetworkRule> SOCIAL_NETWORK_RULES = List.of(
            // WhatsApp's in-app link preview fetcher never sends a Referer header — UA only.
            new SocialNetworkRule("WhatsApp", List.of("%WhatsApp%"), List.of()),
            new SocialNetworkRule("Facebook", List.of("%facebookexternalhit%", "%FacebookBot%"), List.of("facebook.com")),
            new SocialNetworkRule("Discord", List.of("%Discordbot%"), List.of("discord.com")),
            new SocialNetworkRule("Twitter/X", List.of("%Twitterbot%"), List.of("twitter.com", "x.com")));

    // Builds "CASE WHEN ... THEN 'label' ... END", appending a '?' placeholder (and its value, in the
    // same order) to params for every user-agent/referer pattern — referer domains are anchored to the
    // whole host (scheme + optional "www." + domain, followed by "/", ":" or end-of-string) so e.g.
    // "x.com" never matches "...netflix.com/..." (substring) nor "https://x.company.com/..." (prefix only).
    private static String socialNetworkCaseSql(List<Object> params) {
        StringBuilder sql = new StringBuilder("CASE\n");
        for (SocialNetworkRule rule : SOCIAL_NETWORK_RULES) {
            List<String> conditions = new ArrayList<>();
            for (String uaPattern : rule.userAgentPatterns()) {
                conditions.add("user_agent LIKE ?");
                params.add(uaPattern);
            }
            for (String domain : rule.refererDomains()) {
                for (String scheme : List.of("http://", "https://")) {
                    for (String prefix : List.of("", "www.")) {
                        String host = scheme + prefix + domain;
                        conditions.add("(referer = ? OR referer LIKE ? OR referer LIKE ?)");
                        params.add(host);
                        params.add(host + "/%");
                        params.add(host + ":%");
                    }
                }
            }
            sql.append("  WHEN ").append(String.join(" OR ", conditions)).append(" THEN '").append(rule.label()).append("'\n");
        }
        return sql.append("END").toString();
    }

    private static final RowMapper<SocialNetworkRequest> SOCIAL_NETWORK_REQUEST_MAPPER = (rs, _) -> {
        String countryName = resolveCountryDisplayOrNull(rs.getString("country"));
        if (countryName == null) countryName = "-";
        return new SocialNetworkRequest(
                rs.getString("network"), Instant.parse(rs.getString("timestamp")),
                rs.getString("user_agent"), rs.getString("ua_name"), rs.getString("uri_stem"), countryName,
                rs.getLong("hit"), rs.getLong("miss"), rs.getLong(FIELD_FUNCTION), rs.getLong(FIELD_ERROR));
    };

    // Most recent requests attributed to each known social/messaging network within the range,
    // capped per network so one dominant network can't crowd the others out. Restricted to URIs
    // ending in "/" (webpages) — static assets a preview crawler also fetches are noise here.
    public Map<String, List<SocialNetworkRequest>> socialNetworkRequests(Instant from, Instant to, int limitPerNetwork) {
        List<Object> params = new ArrayList<>();
        String caseSql = socialNetworkCaseSql(params);
        params.add(TimestampFormat.sqlValue(from));
        params.add(TimestampFormat.sqlValue(to));
        params.add(limitPerNetwork);

        Map<String, List<SocialNetworkRequest>> byNetwork = new LinkedHashMap<>();
        for (SocialNetworkRule rule : SOCIAL_NETWORK_RULES) byNetwork.put(rule.label(), new ArrayList<>());

        List<SocialNetworkRequest> requests = jdbc.query("""
                SELECT network, timestamp, user_agent, ua_name, uri_stem, country, hit, miss, function, error
                FROM (
                    SELECT *, ROW_NUMBER() OVER (PARTITION BY network ORDER BY timestamp DESC) AS rn
                    FROM (
                        SELECT timestamp, user_agent, ua_name, uri_stem, country,
                               %s AS network,
                               %s
                        FROM cloudfront_logs
                        WHERE timestamp BETWEEN ? AND ?
                          AND uri_stem LIKE '%%/'
                    )
                    WHERE network IS NOT NULL
                )
                WHERE rn <= ?
                ORDER BY network, timestamp DESC
                """.formatted(caseSql, ResultTypeSql.resultTypeFlags("")),
                SOCIAL_NETWORK_REQUEST_MAPPER, params.toArray());
        for (SocialNetworkRequest r : requests) byNetwork.get(r.network()).add(r);
        return byNetwork;
    }

}
