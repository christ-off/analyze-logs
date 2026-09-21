package com.example.analyzelog.service;

import com.example.analyzelog.config.RefererFilterProperties;
import com.example.analyzelog.config.UriStemFilterProperties;
import com.example.analyzelog.config.UriStemGroupProperties;
import com.example.analyzelog.model.BotUaRequest;
import com.example.analyzelog.model.CountryResultTypeCount;
import com.example.analyzelog.model.CountryStats;
import com.example.analyzelog.model.DailyNameCount;
import com.example.analyzelog.model.DailyResultTypeCount;
import com.example.analyzelog.model.HumanTrafficStats;
import com.example.analyzelog.model.IdentityShift;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.model.NameHumanTrafficStats;
import com.example.analyzelog.model.NameResultTypeCount;
import com.example.analyzelog.model.SiteConfigFetcher;
import com.example.analyzelog.model.SocialNetworkRequest;
import com.example.analyzelog.model.UnknownUaRequest;
import com.example.analyzelog.util.TimestampFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
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
    // Every ua_name variant of a browser (desktop and mobile) is "<Browser> / <OS>" — the browser
    // dashboards aggregate across all of them regardless of OS.
    private static final String BROWSER_UA_FILTER = "ua_name LIKE ?";
    private static final String SQL_SELECT_UA_NAME = "SELECT ua_name as name,\n";
    private static final String SQL_SELECT_COUNTRY = "SELECT country as code,\n";
    private static final int UA_COUNTRIES_LIMIT = 10;
    private static final String RESULT_TYPE_EXCLUSION =
            "edge_response_result_type NOT IN ('Error'," + ResultTypeSql.FUNCTION_TYPE_LIST + ")";
    private static final String RESULT_TYPE_GROUP_EXPR =
            "CASE WHEN edge_response_result_type IN (" + ResultTypeSql.FUNCTION_TYPE_LIST + ") THEN 'Filtered' " +
            "WHEN edge_response_result_type IN (" + ResultTypeSql.HIT_TYPE_LIST + ") THEN 'Hit' " +
            "ELSE edge_response_result_type END";
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
    // Every ua_group considered a known bot — reused wherever "not a bot" or "known-bot identity" matters
    // (identityShiftingIps, the Human page's bot exclusion).
    static final String BOT_UA_GROUPS_SQL_LIST = "'AI Bots','Search Bots','Other Bots'";
    // Assets a real browser fetches only when actually rendering the page — the site stylesheet and the
    // "written by a human" badge svg. Neither is ever fetched by a bot/scanner; requiring BOTH (rather
    // than either alone) narrows out a bot/scraper that happens to hotlink just one of the two.
    // The legitimate archives the site serves (DeDRM plugin, sitemap) — excluded from zipUriCounts so only
    // scanner probes for archive dumps remain.
    private static final String LEGITIMATE_ZIP_PATH = "/assets/posts_other/DeDRM_plugin.zip";
    private static final String SITEMAP_GZ_PATH = "/sitemap.xml.gz";
    private static final String HUMAN_EVIDENCE_CSS_PATH = "/css/main.css";
    private static final String HUMAN_EVIDENCE_SVG_PATH = "/assets/svgs/ecrit-par-un-humain.svg";
    // A "page" request (uri_stem ending in '/') from a non-bot ua_group, corroborated by requests from
    // the same (client_ip, user_agent) for both evidence assets above, each within +/-1h — the Human
    // page's definition of a genuine human page-view. Restricting to a 1h window (rather than "ever", as
    // trafficCategories()/categoryCaseExpr do) rules out a bot that later replays a human IP/UA pair long
    // after the human visit ended.
    private static final String HUMAN_PAGE_FILTER =
            "uri_stem LIKE '%/'\n" +
            "  AND ua_name NOT IN (SELECT ua_name FROM static_ua WHERE ua_group IN (" + BOT_UA_GROUPS_SQL_LIST + "))\n" +
            "  " + withinOneHourExistsClause("m1", HUMAN_EVIDENCE_CSS_PATH) + "\n" +
            "  " + withinOneHourExistsClause("m2", HUMAN_EVIDENCE_SVG_PATH);
    // Only Hit/Miss responses count as real traffic — Error and FunctionGeneratedResponse rows
    // (scanners, filtered requests) are excluded from these predicates. RefreshHit counts as a Hit.
    private static final String RESULT_TYPE_HIT_OR_MISS =
            "edge_response_result_type IN (" + ResultTypeSql.HIT_TYPE_LIST + ", 'Miss')";
    private static final String HUMAN_EVIDENCE_CSS_PREDICATE = "uri_stem = '" + HUMAN_EVIDENCE_CSS_PATH + "'";
    private static final String HUMAN_EVIDENCE_SVG_PREDICATE = "uri_stem = '" + HUMAN_EVIDENCE_SVG_PATH + "'";
    // Any pair (client_ip, user_agent) requesting one of these is classified as the 'Feeds' category.
    private static final String FEED_URI_LIST = "'/feed.xml','/rss.xml'";
    // Pair classification used to label rows (trafficCategories) and to scope human-traffic
    // stats to a UA or country (humanTrafficStats/countryHumanTrafficStats).
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
                 AND MAX(CASE WHEN %s AND %s THEN 1 ELSE 0 END) = 1
                    THEN 'Probable human'
                WHEN MAX(CASE WHEN uri_stem = '/robots.txt' THEN 1 ELSE 0 END) = 1
                    THEN 'Declared bots'
                ELSE 'Other'
            END""";

    // strftime (not datetime()) keeps the 'T'/'Z' ISO-8601 shape of the stored timestamp column —
    // datetime() reformats to a space-separated string that would sort before/after it inconsistently in
    // the BETWEEN comparison below, since cloudfront_logs.timestamp is TEXT compared lexicographically.
    private static String withinOneHourExistsClause(String alias, String uriStem) {
        return "AND EXISTS (\n" +
                "    SELECT 1 FROM cloudfront_logs " + alias + "\n" +
                "    WHERE " + alias + ".client_ip = cloudfront_logs.client_ip\n" +
                "      AND " + alias + ".user_agent = cloudfront_logs.user_agent\n" +
                "      AND " + alias + ".uri_stem = '" + uriStem + "'\n" +
                "      AND " + alias + ".timestamp BETWEEN strftime('%Y-%m-%dT%H:%M:%SZ', cloudfront_logs.timestamp, '-1 hour')\n" +
                "                                       AND strftime('%Y-%m-%dT%H:%M:%SZ', cloudfront_logs.timestamp, '+1 hour')\n" +
                "  )";
    }

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
                RESULT_TYPE_HIT_OR_MISS,
                HUMAN_EVIDENCE_CSS_PREDICATE, RESULT_TYPE_HIT_OR_MISS,
                HUMAN_EVIDENCE_SVG_PREDICATE, RESULT_TYPE_HIT_OR_MISS);
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
                     "  AND c." + RESULT_TYPE_HIT_OR_MISS + "\n" +
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

    // Same aggregation, scoped to qualifying "Human" page requests — see HUMAN_PAGE_FILTER.
    public List<NameResultTypeCount> humanTopUserAgentsByResultType(Instant from, Instant to, int limit) {
        return uaResultTypesByFilter(HUMAN_PAGE_FILTER, List.of(), from, to, limit);
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

    // All countries with result-type counts and the "Probable human" request share (see trafficCategories()).
    public List<CountryStats> countryStats(Instant from, Instant to) {
        String sql = """
                WITH pair_class AS (
                    SELECT client_ip, user_agent,
                        %s AS category
                    FROM cloudfront_logs
                    WHERE timestamp BETWEEN ? AND ?
                    GROUP BY client_ip, user_agent
                )
                SELECT c.country AS code,
                       %s,
                       SUM(CASE WHEN pc.category = 'Probable human' AND c.uri_stem NOT LIKE '%%.webp' THEN 1 ELSE 0 END) AS human,
                       SUM(CASE WHEN c.uri_stem NOT LIKE '%%.webp' THEN 1 ELSE 0 END) AS non_webp,
                       SUM(CASE WHEN c.ua_name = 'Mastodon' THEN 1 ELSE 0 END) AS mastodon
                FROM cloudfront_logs c
                JOIN pair_class pc ON c.client_ip = pc.client_ip AND c.user_agent = pc.user_agent
                WHERE c.timestamp BETWEEN ? AND ?
                  AND c.country IS NOT NULL
                GROUP BY c.country
                ORDER BY (hit + miss + function + error) DESC
                """.formatted(categoryCaseExpr, ResultTypeSql.resultTypeSums("c"));
        String fromSql = TimestampFormat.sqlValue(from);
        String toSql = TimestampFormat.sqlValue(to);
        return jdbc.query(sql, (rs, _) -> {
            String iso = rs.getString("code");
            return new CountryStats(iso, resolveCountryLabel(iso),
                    rs.getLong("hit"), rs.getLong("miss"), rs.getLong(FIELD_FUNCTION), rs.getLong(FIELD_ERROR),
                    rs.getLong("human"), rs.getLong("non_webp"), rs.getLong("mastodon"));
        }, fromSql, toSql, fromSql, toSql);
    }

    public List<CountryResultTypeCount> topCountriesByResultType(Instant from, Instant to, int limit) {
        return countryResultTypesByFilter("", List.of(), from, to, limit);
    }

    // Same aggregation, scoped to qualifying "Human" page requests — see HUMAN_PAGE_FILTER.
    public List<CountryResultTypeCount> humanTopCountriesByResultType(Instant from, Instant to, int limit) {
        return countryResultTypesByFilter(HUMAN_PAGE_FILTER, List.of(), from, to, limit);
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

    // Same aggregation, scoped to qualifying "Human" page requests — see HUMAN_PAGE_FILTER.
    public List<NameResultTypeCount> humanTopUrlsByResultType(Instant from, Instant to, int limit) {
        return urlsByResultType(HUMAN_PAGE_FILTER, List.of(TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to)), limit);
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
                  AND\s""" + RESULT_TYPE_HIT_OR_MISS + """

                GROUP BY name
                ORDER BY count DESC
                """;
        return jdbc.query(sql, NAME_COUNT_MAPPER, TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to));
    }

    public List<NameCount> topReferers(Instant from, Instant to, int limit) {
        return topReferersByFilter("", List.of(), from, to, limit);
    }

    // Same referer aggregation, scoped to qualifying "Human" page requests — see HUMAN_PAGE_FILTER.
    public List<NameCount> humanTopReferers(Instant from, Instant to, int limit) {
        return topReferersByFilter(HUMAN_PAGE_FILTER, List.of(), from, to, limit);
    }

    private List<NameCount> topReferersByFilter(String additionalFilter, List<Object> extraArgs,
                                                 Instant from, Instant to, int limit) {
        String sql = "SELECT referer as name, COUNT(*) as count\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                "  AND referer IS NOT NULL\n" +
                "  AND " + RESULT_TYPE_EXCLUSION + "\n" +
                andClause(selfExclusionClause) +
                andClause(additionalFilter) +
                "GROUP BY referer\n" +
                "ORDER BY count DESC\n";

        var args = new ArrayList<>();
        args.add(TimestampFormat.sqlValue(from));
        args.add(TimestampFormat.sqlValue(to));
        args.addAll(selfExclusionPatterns);
        args.addAll(extraArgs);

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

    private static String browserUaPattern(String browser) {
        return browser + " / %";
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

    public List<NameResultTypeCount> browserRawUserAgents(String browser, Instant from, Instant to) {
        return rawUserAgentsByFilter(BROWSER_UA_FILTER, browserUaPattern(browser), from, to);
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

    public List<NameHumanTrafficStats> browserHumanTraffic(String browser, Instant from, Instant to) {
        return humanTrafficByUserAgent(BROWSER_UA_FILTER, browserUaPattern(browser), from, to);
    }

    public List<NameCount> uaResultTypes(String uaName, Instant from, Instant to) {
        return queryResultTypesByFilter(UA_NAME_FILTER, uaName, from, to);
    }

    public List<NameCount> browserResultTypes(String browser, Instant from, Instant to) {
        return queryResultTypesByFilter(BROWSER_UA_FILTER, browserUaPattern(browser), from, to);
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

    public List<NameCount> browserCountries(String browser, Instant from, Instant to) {
        return countriesByFilter(BROWSER_UA_FILTER, browserUaPattern(browser), from, to);
    }

    public List<NameResultTypeCount> uaUrlsByResultType(String uaName, Instant from, Instant to, int limit) {
        return urlsByResultType(UA_NAME_FILTER, List.of(TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), uaName), limit);
    }

    public List<NameResultTypeCount> browserUrlsByResultType(String browser, Instant from, Instant to, int limit) {
        return urlsByResultType(BROWSER_UA_FILTER, List.of(TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), browserUaPattern(browser)), limit);
    }

    private List<DailyResultTypeCount> requestsPerDayByFilter(String filterClause, Object filterArg, Instant from, Instant to) {
        return queryDailyByResultType(SQL_DAILY_SELECT + "  AND " + filterClause + "\n" + SQL_DAILY_GROUP_ORDER,
                TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), filterArg);
    }

    public List<DailyResultTypeCount> uaRequestsPerDay(String uaName, Instant from, Instant to) {
        return requestsPerDayByFilter(UA_NAME_FILTER, uaName, from, to);
    }

    public List<DailyResultTypeCount> browserRequestsPerDay(String browser, Instant from, Instant to) {
        return requestsPerDayByFilter(BROWSER_UA_FILTER, browserUaPattern(browser), from, to);
    }

    public List<DailyResultTypeCount> requestsPerDay(Instant from, Instant to) {
        String sql = SQL_DAILY_SELECT + SQL_DAILY_GROUP_ORDER;
        return queryDailyByResultType(sql, TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to));
    }

    // Same aggregation, scoped to qualifying "Human" page requests — see HUMAN_PAGE_FILTER.
    public List<DailyResultTypeCount> humanRequestsPerDay(Instant from, Instant to) {
        String sql = SQL_DAILY_SELECT + andClause(HUMAN_PAGE_FILTER) + SQL_DAILY_GROUP_ORDER;
        return queryDailyByResultType(sql, TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to));
    }

    // Individual qualifying "Human" page requests (see HUMAN_PAGE_FILTER) whose user agent
    // UserAgentClassifier fell through to "Unknown" — surfaced on the Human page so an operator
    // can see which raw UA strings are behind that bucket.
    public List<UnknownUaRequest> unknownUaRequests(Instant from, Instant to, int limit) {
        String sql = "SELECT timestamp, user_agent, uri_stem,\n" + ResultTypeSql.resultTypeFlags("") + "\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                "  AND ua_name = 'Unknown'\n" +
                andClause(HUMAN_PAGE_FILTER) +
                "ORDER BY timestamp DESC\n" +
                LIMIT_PARAM;
        return jdbc.query(sql, UNKNOWN_UA_REQUEST_MAPPER, TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), limit);
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
                SUM(CASE WHEN uri_stem NOT LIKE '%.%' AND uri_stem != '/' AND edge_response_result_type IN ('Hit','RefreshHit') THEN 1 ELSE 0 END) AS hit,
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

    // package-private (exercised directly by DashboardServiceIntegrationTest); additionalFilter is
    // reserved for reuse (e.g. "ua_name = ?", "country = ?") beyond the current humanTrafficStats callers.
    // excludeWebp: drop .webp requests from the outer per-request count (they're kept as
    // "Probable human" evidence in the pair_class CTE) — used for the human-traffic proportion,
    // where bulk webp asset downloads shouldn't inflate the request totals.
    List<NameResultTypeCount> trafficCategories(String additionalFilter, List<Object> extraArgs,
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
                ORDER BY timestamp DESC, id DESC
                """.formatted(RESULT_TYPE_GROUP_EXPR);
        return jdbc.query(sql, BOT_UA_REQUEST_MAPPER, ua, TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to));
    }

    public List<DailyResultTypeCount> requestsPerDayByUserAgent(String ua, Instant from, Instant to) {
        return queryDailyByResultType(SQL_DAILY_SELECT + "  AND user_agent = ?\n" + SQL_DAILY_GROUP_ORDER,
                TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), ua);
    }

    // Browser-classified UAs requesting site config files — robots.txt, ads.txt, sitemap.xml
    // and other files real browsers never fetch on their own. Also reports how many other
    // (non-config) requests the same UA made, since a real browser that stumbles into one of
    // these paths still browses the rest of the site, while a bot mostly won't.
    public List<SiteConfigFetcher> browserConfigFetches(Instant from, Instant to, int limit) {
        return jdbc.query("""
                SELECT c.user_agent AS name,
                       SUM(CASE WHEN c.uri_stem IN (%1$s) AND c.edge_response_result_type IN (%3$s) THEN 1 ELSE 0 END) AS hit,
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
                """.formatted(SITE_CONFIG_PATHS_SQL_LIST, ResultTypeSql.FUNCTION_TYPE_LIST, ResultTypeSql.HIT_TYPE_LIST)
                + LIMIT_PARAM,
                SITE_CONFIG_FETCHER_MAPPER,
                TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), limit);
    }

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
                """.formatted(BOT_UA_GROUPS_SQL_LIST),
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

    // Link-preview crawler UA substrings, classifier ua_names and click-through referer domains for each
    // known social/messaging network. Order matters: first match wins for requests that could match more
    // than one. excludeRootUri drops hits on "/" for networks whose crawlers poll the home page so often
    // that the genuine article previews would be buried.
    private record SocialNetworkRule(String label, List<String> userAgentPatterns, List<String> refererDomains,
                                     List<String> uaNames, boolean excludeRootUri) {
        SocialNetworkRule(String label, List<String> userAgentPatterns, List<String> refererDomains) {
            this(label, userAgentPatterns, refererDomains, List.of(), false);
        }
    }

    private static final List<SocialNetworkRule> SOCIAL_NETWORK_RULES = List.of(
            // Fediverse instances fan out a preview fetch per server, with UA strings too varied to match
            // by substring — rely on the user-agent classifier's ua_name instead.
            new SocialNetworkRule("Mastodon", List.of(), List.of(), List.of("Mastodon"), true),
            // WhatsApp's in-app link preview fetcher never sends a Referer header — UA only.
            new SocialNetworkRule("WhatsApp", List.of("%WhatsApp%"), List.of()),
            new SocialNetworkRule("Facebook", List.of("%facebookexternalhit%", "%FacebookBot%"), List.of("facebook.com")));

    // Builds "CASE WHEN ... THEN 'label' ... END", appending a '?' placeholder (and its value, in the
    // same order) to params for every user-agent/referer pattern — referer domains are anchored to the
    // whole host (scheme + optional "www." + domain, followed by "/", ":" or end-of-string) so e.g.
    // "facebook.com" never matches "...notfacebook.com/..." (substring) nor
    // "https://facebook.com.evil.example/..." (prefix only).
    private static String socialNetworkCaseSql(List<Object> params) {
        StringBuilder sql = new StringBuilder("CASE\n");
        for (SocialNetworkRule rule : SOCIAL_NETWORK_RULES) {
            List<String> conditions = new ArrayList<>();
            for (String uaPattern : rule.userAgentPatterns()) {
                conditions.add("user_agent LIKE ?");
                params.add(uaPattern);
            }
            for (String uaName : rule.uaNames()) {
                conditions.add("ua_name = ?");
                params.add(uaName);
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
            String match = String.join(" OR ", conditions);
            if (rule.excludeRootUri()) match = "(" + match + ") AND uri_stem <> '/'";
            sql.append("  WHEN ").append(match).append(" THEN '").append(rule.label()).append("'\n");
        }
        return sql.append("END").toString();
    }

    private static final RowMapper<UnknownUaRequest> UNKNOWN_UA_REQUEST_MAPPER = (rs, _) ->
            new UnknownUaRequest(Instant.parse(rs.getString("timestamp")), rs.getString("user_agent"), rs.getString("uri_stem"),
                    rs.getLong("hit"), rs.getLong("miss"), rs.getLong(FIELD_FUNCTION), rs.getLong(FIELD_ERROR));

    private static SocialNetworkRequest socialNetworkRequest(ResultSet rs) throws SQLException {
        String countryName = resolveCountryDisplayOrNull(rs.getString("country"));
        return new SocialNetworkRequest(Instant.parse(rs.getString("timestamp")), rs.getString("user_agent"),
                rs.getString("ua_name"), rs.getString("uri_stem"), countryName == null ? "-" : countryName);
    }

    // Most recent requests attributed to each known social/messaging network within the range,
    // capped per network so one dominant network can't crowd the others out. Restricted to URIs
    // ending in "/" (webpages) — static assets a preview crawler also fetches are noise here — and to
    // real traffic: a Filtered or Error response says nothing about a shared link.
    public Map<String, List<SocialNetworkRequest>> socialNetworkRequests(Instant from, Instant to, int limitPerNetwork) {
        List<Object> params = new ArrayList<>();
        String caseSql = socialNetworkCaseSql(params);
        params.add(TimestampFormat.sqlValue(from));
        params.add(TimestampFormat.sqlValue(to));
        params.add(limitPerNetwork);

        Map<String, List<SocialNetworkRequest>> byNetwork = new LinkedHashMap<>();
        for (SocialNetworkRule rule : SOCIAL_NETWORK_RULES) byNetwork.put(rule.label(), new ArrayList<>());

        jdbc.query("""
                SELECT network, timestamp, user_agent, ua_name, uri_stem, country
                FROM (
                    SELECT *, ROW_NUMBER() OVER (PARTITION BY network ORDER BY timestamp DESC) AS rn
                    FROM (
                        SELECT timestamp, user_agent, ua_name, uri_stem, country,
                               %s AS network
                        FROM cloudfront_logs
                        WHERE timestamp BETWEEN ? AND ?
                          AND uri_stem LIKE '%%/'
                          AND %s
                    )
                    WHERE network IS NOT NULL
                )
                WHERE rn <= ?
                ORDER BY network, timestamp DESC
                """.formatted(caseSql, RESULT_TYPE_HIT_OR_MISS),
                (RowCallbackHandler) (rs ->
                        byNetwork.get(rs.getString("network")).add(socialNetworkRequest(rs))),
                params.toArray());
        return byNetwork;
    }

    // uri_stems behind 404/Error requests, most frequent first.
    public List<NameCount> errors404UriCounts(Instant from, Instant to, int limit) {
        String sql = """
                SELECT uri_stem as name, COUNT(*) as count
                FROM cloudfront_logs
                WHERE timestamp BETWEEN ? AND ?
                  AND status = 404 AND edge_response_result_type = 'Error'
                GROUP BY uri_stem
                ORDER BY count DESC
                LIMIT ?
                """;
        return jdbc.query(sql, NAME_COUNT_MAPPER,
                TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to), limit);
    }

    // Archive (.zip/.gz/.tgz/.rar/.7z) uri_stems requested, excluding the one legitimate asset, most frequent first.
    public List<NameCount> zipUriCounts(Instant from, Instant to, int limit) {
        String sql = """
                SELECT uri_stem as name, COUNT(*) as count
                FROM cloudfront_logs
                WHERE timestamp BETWEEN ? AND ?
                  AND (uri_stem LIKE '%.zip' OR uri_stem LIKE '%.gz' OR uri_stem LIKE '%.tgz'
                       OR uri_stem LIKE '%.rar' OR uri_stem LIKE '%.7z')
                  AND uri_stem NOT IN (?, ?)
                GROUP BY uri_stem
                ORDER BY count DESC
                LIMIT ?
                """;
        return jdbc.query(sql, NAME_COUNT_MAPPER,
                TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to),
                LEGITIMATE_ZIP_PATH, SITEMAP_GZ_PATH, limit);
    }

}
