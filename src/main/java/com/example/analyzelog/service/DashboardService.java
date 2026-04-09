package com.example.analyzelog.service;

import com.example.analyzelog.config.RefererFilterProperties;
import com.example.analyzelog.config.RefererNormalizerProperties;
import com.example.analyzelog.config.UriStemFilterProperties;
import com.example.analyzelog.model.CountryResultTypeCount;
import com.example.analyzelog.model.DailyResultTypeCount;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.model.NameResultTypeCount;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class DashboardService {

    private static final String COUNT_FIELD = "count";
    private static final String FIELD_FUNCTION = "function";
    private static final String FIELD_ERROR = "error";
    private static final String FIELD_REDIRECT = "redirect";
    private static final String AND_SEPARATOR = " AND ";
    private static final String SQL_AND_INDENT = "  AND ";
    private static final Set<String> BOT_GROUP_NAMES =
            Set.of("AI Bots", "Search Bots", "Other Bots", "Apps");
    private static final String URI_STEM_EXCLUSION_PREDICATE = "uri_stem NOT LIKE ?";
    private static final String URI_STEM_NAME_CASE = """
            CASE
                WHEN uri_stem LIKE '/wp-%' THEN 'WordPress'
                WHEN uri_stem LIKE '//wp-%' THEN 'WordPress'
                WHEN uri_stem LIKE '/wordpress/%' THEN 'WordPress'
                WHEN uri_stem LIKE '/wp/%' THEN 'WordPress'
                WHEN uri_stem LIKE '%.php' THEN 'PHP'
                WHEN LOWER(uri_stem) LIKE '%.php7' THEN 'PHP'
                ELSE uri_stem
            END as name,
            """;
    private static final String RESULT_TYPE_SUMS = """
            SUM(CASE WHEN edge_response_result_type = 'Hit'    THEN 1 ELSE 0 END) as hit,
            SUM(CASE WHEN edge_response_result_type = 'Miss'   THEN 1 ELSE 0 END) as miss,
            SUM(CASE WHEN edge_response_result_type IN (
                    'FunctionGeneratedResponse',
                    'FunctionExecutionError',
                    'FunctionThrottledError')                  THEN 1 ELSE 0 END) as function,
            SUM(CASE WHEN edge_response_result_type = 'Error'    THEN 1 ELSE 0 END) as error,
            SUM(CASE WHEN edge_response_result_type = 'Redirect' THEN 1 ELSE 0 END) as redirect\
            """;
    private static final String SQL_URI_BY_RESULT_TYPE = "SELECT \n" +
            URI_STEM_NAME_CASE +
            RESULT_TYPE_SUMS + "\n" +
            "FROM cloudfront_logs\n" +
            "WHERE timestamp BETWEEN ? AND ?\n";
    private static final String SQL_URI_RESULT_TYPE_GROUP_ORDER = """
            GROUP BY name
            ORDER BY (hit + miss + function + error + redirect) DESC
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
    private final UaGroupClassifier uaGroupClassifier;
    private final List<String> excludedExtensions;
    private final List<String> selfReferers;
    private final List<RefererNormalizerProperties.Rule> normalizerRules;
    private final List<String> botUaLabels;
    private final List<String> botUaPrefixes;

    public DashboardService(JdbcTemplate jdbc, EdgeLocationResolver edgeLocationResolver,
                            UaGroupClassifier uaGroupClassifier,
                            UriStemFilterProperties uriStemFilterProperties,
                            RefererFilterProperties refererFilterProperties,
                            RefererNormalizerProperties refererNormalizerProperties) {
        this.jdbc = jdbc;
        this.edgeLocationResolver = edgeLocationResolver;
        this.uaGroupClassifier = uaGroupClassifier;
        this.excludedExtensions = uriStemFilterProperties.excludedExtensions();
        this.selfReferers = refererFilterProperties.selfReferers();
        this.normalizerRules = refererNormalizerProperties.rules();
        this.botUaLabels   = uaGroupClassifier.labelsForGroups(BOT_GROUP_NAMES);
        this.botUaPrefixes = uaGroupClassifier.prefixesForGroups(BOT_GROUP_NAMES);
    }

    private String uriStemExclusionClause() {
        return excludedExtensions.stream()
                .map(_ -> URI_STEM_EXCLUSION_PREDICATE)
                .collect(Collectors.joining(AND_SEPARATOR));
    }

    private static String andClause(String clause) {
        return clause.isEmpty() ? "" : SQL_AND_INDENT + clause + "\n";
    }

    private String botExclusionClause() {
        String inClause = botUaLabels.isEmpty() ? "" :
                "ua_name NOT IN (" +
                botUaLabels.stream().map(_ -> "?").collect(Collectors.joining(",")) +
                ")";
        String likeClause = botUaPrefixes.stream()
                .map(_ -> "ua_name NOT LIKE ?")
                .collect(Collectors.joining(AND_SEPARATOR));
        return Stream.of("ua_name != '(no user agent)'", inClause, likeClause)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(AND_SEPARATOR));
    }

    private void addBotExclusionArgs(List<Object> args) {
        args.addAll(botUaLabels);
        botUaPrefixes.forEach(p -> args.add(p + "%"));
    }

    public List<NameCount> uaGroupCounts(Instant from, Instant to) {
        return uaGroupCounts(from, to, false);
    }

    public List<NameCount> uaGroupCounts(Instant from, Instant to, boolean excludeBots) {
        String exclusion = excludeBots ? andClause(botExclusionClause()) : "";
        String sql = "SELECT ua_name as name, COUNT(*) as count\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                "  AND ua_name != '(no user agent)'\n" +
                exclusion +
                "GROUP BY ua_name\n";
        var args = new ArrayList<>();
        args.add(from.toString());
        args.add(to.toString());
        if (excludeBots) addBotExclusionArgs(args);
        List<NameCount> rawCounts = jdbc.query(sql,
                (rs, _) -> new NameCount(rs.getString("name"), rs.getLong(COUNT_FIELD)),
                args.toArray());
        return aggregateByLabel(rawCounts, uaGroupClassifier::classify);
    }

    public List<NameResultTypeCount> topUserAgentsByResultType(Instant from, Instant to, int limit) {
        return topUserAgentsByResultType(from, to, limit, false);
    }

    public List<NameResultTypeCount> topUserAgentsByResultType(Instant from, Instant to, int limit, boolean excludeBots) {
        String exclusion = excludeBots ? andClause(botExclusionClause()) : "";
        String sql = "SELECT ua_name as name,\n" + RESULT_TYPE_SUMS + "\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                exclusion +
                "GROUP BY ua_name\n" +
                "ORDER BY (hit + miss + function + error + redirect) DESC\n" +
                "LIMIT ?\n";
        var args = new ArrayList<>();
        args.add(from.toString());
        args.add(to.toString());
        if (excludeBots) addBotExclusionArgs(args);
        args.add(limit);
        return jdbc.query(sql,
                (rs, _) -> new NameResultTypeCount(
                        rs.getString("name"),
                        rs.getLong("hit"),
                        rs.getLong("miss"),
                        rs.getLong(FIELD_FUNCTION),
                        rs.getLong(FIELD_ERROR),
                        rs.getLong(FIELD_REDIRECT)),
                args.toArray());
    }

    public List<CountryResultTypeCount> topCountriesByResultType(Instant from, Instant to, int limit) {
        return topCountriesByResultType(from, to, limit, false);
    }

    public List<CountryResultTypeCount> topCountriesByResultType(Instant from, Instant to, int limit, boolean excludeBots) {
        String exclusion = excludeBots ? andClause(botExclusionClause()) : "";
        String sql = "SELECT country as code,\n" + RESULT_TYPE_SUMS + "\n" +
                "FROM cloudfront_logs\n" +
                "WHERE timestamp BETWEEN ? AND ?\n" +
                "  AND country IS NOT NULL\n" +
                exclusion +
                "GROUP BY country\n" +
                "ORDER BY (hit + miss + function + error + redirect) DESC\n" +
                "LIMIT ?\n";
        var args = new ArrayList<>();
        args.add(from.toString());
        args.add(to.toString());
        if (excludeBots) addBotExclusionArgs(args);
        args.add(limit);
        return jdbc.query(sql,
                (rs, _) -> {
                    String iso = rs.getString("code");
                    String display = (iso != null && !iso.isBlank())
                            ? Locale.of("", iso).getDisplayCountry(Locale.ENGLISH)
                            : iso;
                    String label = (display != null && !display.isBlank() && !display.equals(iso)) ? display : iso;
                    return new CountryResultTypeCount(iso, label,
                            rs.getLong("hit"),
                            rs.getLong("miss"),
                            rs.getLong(FIELD_FUNCTION),
                            rs.getLong(FIELD_ERROR),
                            rs.getLong(FIELD_REDIRECT));
                },
                args.toArray());
    }

    public List<NameCount> countryResultTypes(String countryCode, Instant from, Instant to) {
        return queryResultTypesByFilter("country", countryCode, from, to);
    }

    public List<NameResultTypeCount> countryUrlsByResultType(String countryCode, Instant from, Instant to, int limit) {
        return urlsByResultType("country = ?", List.of(from.toString(), to.toString(), countryCode), limit);
    }

    public List<DailyResultTypeCount> countryRequestsPerDay(String countryCode, Instant from, Instant to) {
        return queryDailyByResultType(SQL_DAILY_SELECT + "  AND country = ?\n" + SQL_DAILY_GROUP_ORDER,
                from.toString(), to.toString(), countryCode);
    }

    public List<NameResultTypeCount> topUrlsByResultType(Instant from, Instant to, int limit) {
        return topUrlsByResultType(from, to, limit, false);
    }

    public List<NameResultTypeCount> topUrlsByResultType(Instant from, Instant to, int limit, boolean excludeBots) {
        return urlsByResultType("", List.of(from.toString(), to.toString()), limit, excludeBots);
    }

    private List<NameResultTypeCount> urlsByResultType(String additionalFilter, List<Object> baseArgs, int limit) {
        return urlsByResultType(additionalFilter, baseArgs, limit, false);
    }

    private List<NameResultTypeCount> urlsByResultType(String additionalFilter, List<Object> baseArgs, int limit, boolean excludeBots) {
        String exclusionClause = uriStemExclusionClause();
        String botClause = excludeBots ? botExclusionClause() : "";
        String combinedFilter = Stream.of(additionalFilter, botClause)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(AND_SEPARATOR));
        String sql = SQL_URI_BY_RESULT_TYPE
                + andClause(combinedFilter)
                + andClause(exclusionClause)
                + SQL_URI_RESULT_TYPE_GROUP_ORDER;

        var args = new ArrayList<>(baseArgs);
        if (excludeBots) addBotExclusionArgs(args);
        excludedExtensions.forEach(ext -> args.add("%." + ext.replaceFirst("^\\.", "")));
        args.add(limit);

        return jdbc.query(sql,
                (rs, _) -> new NameResultTypeCount(
                        rs.getString("name"),
                        rs.getLong("hit"),
                        rs.getLong("miss"),
                        rs.getLong(FIELD_FUNCTION),
                        rs.getLong(FIELD_ERROR),
                        rs.getLong(FIELD_REDIRECT)),
                args.toArray());
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
                (rs, _) -> new NameCount(
                        edgeLocationResolver.resolveDisplay(rs.getString("iata")),
                        rs.getLong(COUNT_FIELD)),
                from.toString(), to.toString(), limit);
    }

    public List<NameCount> topReferers(Instant from, Instant to, int limit) {
        return topReferers(from, to, limit, false);
    }

    public List<NameCount> topReferers(Instant from, Instant to, int limit, boolean excludeBots) {
        List<String> exclusionPatterns = selfReferers.stream()
                .flatMap(prefix -> selfExclusionPatterns(prefix).stream())
                .toList();
        String selfExclusionClause = exclusionPatterns.stream()
                .map(_ -> "referer NOT LIKE ?")
                .collect(Collectors.joining(AND_SEPARATOR));
        String botClause = excludeBots ? botExclusionClause() : "";
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
        if (excludeBots) addBotExclusionArgs(args);

        List<NameCount> raw = jdbc.query(sql,
                (rs, _) -> new NameCount(rs.getString("name"), rs.getLong(COUNT_FIELD)),
                args.toArray());

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

    public List<NameCount> uaResultTypes(String uaName, Instant from, Instant to) {
        return queryResultTypesByFilter("ua_name", uaName, from, to);
    }

    public List<NameCount> uaCountries(String uaName, Instant from, Instant to) {
        return jdbc.query("""
                SELECT country as name, COUNT(*) as count
                FROM cloudfront_logs
                WHERE timestamp BETWEEN ? AND ?
                  AND ua_name = ?
                GROUP BY country
                ORDER BY count DESC
                LIMIT 10
                """,
                (rs, _) -> {
                    String iso = rs.getString("name");
                    String display = (iso != null && !iso.isBlank())
                            ? Locale.of("", iso).getDisplayCountry(Locale.ENGLISH)
                            : null;
                    return new NameCount(display != null ? display : iso, rs.getLong(COUNT_FIELD));
                },
                from.toString(), to.toString(), uaName);
    }

    public List<NameResultTypeCount> uaUrlsByResultType(String uaName, Instant from, Instant to, int limit) {
        return urlsByResultType("ua_name = ?", List.of(from.toString(), to.toString(), uaName), limit);
    }

    public List<DailyResultTypeCount> uaRequestsPerDay(String uaName, Instant from, Instant to) {
        return queryDailyByResultType(SQL_DAILY_SELECT + "  AND ua_name = ?\n" + SQL_DAILY_GROUP_ORDER,
                from.toString(), to.toString(), uaName);
    }

    public List<DailyResultTypeCount> requestsPerDay(Instant from, Instant to) {
        return requestsPerDay(from, to, false);
    }

    public List<DailyResultTypeCount> requestsPerDay(Instant from, Instant to, boolean excludeBots) {
        String exclusion = excludeBots ? andClause(botExclusionClause()) : "";
        String sql = SQL_DAILY_SELECT + exclusion + SQL_DAILY_GROUP_ORDER;
        var args = new ArrayList<>();
        args.add(from.toString());
        args.add(to.toString());
        if (excludeBots) addBotExclusionArgs(args);
        return queryDailyByResultType(sql, args.toArray());
    }

    private List<DailyResultTypeCount> queryDailyByResultType(String sql, Object... args) {
        return jdbc.query(sql,
                (rs, _) -> new DailyResultTypeCount(
                        LocalDate.parse(rs.getString("day")),
                        rs.getLong("hit"),
                        rs.getLong("miss"),
                        rs.getLong(FIELD_FUNCTION),
                        rs.getLong(FIELD_ERROR),
                        rs.getLong(FIELD_REDIRECT)),
                args);
    }

    // filterColumn is always a trusted Java constant, never user input
    private List<NameCount> queryResultTypesByFilter(String filterColumn, Object value, Instant from, Instant to) {
        String sql = "SELECT edge_response_result_type as name, COUNT(*) as count\n"
                + "FROM cloudfront_logs\n"
                + "WHERE timestamp BETWEEN ? AND ?\n"
                + "  AND " + filterColumn + " = ?\n"
                + "GROUP BY edge_response_result_type\n"
                + "ORDER BY count DESC\n";
        return jdbc.query(sql,
                (rs, _) -> new NameCount(rs.getString("name"), rs.getLong(COUNT_FIELD)),
                from.toString(), to.toString(), value);
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