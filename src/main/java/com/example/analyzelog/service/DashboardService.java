package com.example.analyzelog.service;

import com.example.analyzelog.config.RefererFilterProperties;
import com.example.analyzelog.config.UriStemFilterProperties;
import com.example.analyzelog.model.CountryCount;
import com.example.analyzelog.model.DailyResultTypeCount;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.model.NameResultTypeCount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final String COUNT_FIELD = "count";
    private static final String FIELD_FUNCTION = "function";
    private static final String FIELD_ERROR = "error";
    private static final String FIELD_REDIRECT = "redirect";
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
    private final JdbcTemplate jdbc;
    private final EdgeLocationResolver edgeLocationResolver;
    private final List<String> excludedExtensions;
    private final List<String> selfReferers;

    public DashboardService(JdbcTemplate jdbc, EdgeLocationResolver edgeLocationResolver,
                            UriStemFilterProperties uriStemFilterProperties,
                            RefererFilterProperties refererFilterProperties) {
        this.jdbc = jdbc;
        this.edgeLocationResolver = edgeLocationResolver;
        this.excludedExtensions = uriStemFilterProperties.excludedExtensions();
        this.selfReferers = refererFilterProperties.selfReferers();
    }

    public List<NameResultTypeCount> topUserAgentsByResultType(Instant from, Instant to, int limit) {
        return jdbc.query("""
                SELECT ua_name as name,
                """ + RESULT_TYPE_SUMS + """

                FROM cloudfront_logs
                WHERE timestamp BETWEEN ? AND ?
                GROUP BY ua_name
                ORDER BY (hit + miss + function + error + redirect) DESC
                LIMIT ?
                """,
                (rs, _) -> new NameResultTypeCount(
                        rs.getString("name"),
                        rs.getLong("hit"),
                        rs.getLong("miss"),
                        rs.getLong(FIELD_FUNCTION),
                        rs.getLong(FIELD_ERROR),
                        rs.getLong(FIELD_REDIRECT)),
                from.toString(), to.toString(), limit);
    }

    public List<CountryCount> topBlockedCountries(Instant from, Instant to, int limit) {
        return jdbc.query("""
                SELECT country as code, COUNT(*) as count
                FROM cloudfront_logs
                WHERE timestamp BETWEEN ? AND ?
                  AND status = 403
                GROUP BY country
                ORDER BY count DESC
                LIMIT ?
                """,
                (rs, _) -> {
                    String iso = rs.getString("code");
                    String display = (iso != null && !iso.isBlank())
                            ? Locale.of("", iso).getDisplayCountry(Locale.ENGLISH)
                            : iso;
                    String label = (display != null && !display.isBlank() && !display.equals(iso)) ? display : iso;
                    return new CountryCount(iso, label, rs.getLong(COUNT_FIELD));
                },
                from.toString(), to.toString(), limit);
    }

    public List<NameCount> countryResultTypes(String countryCode, Instant from, Instant to) {
        return jdbc.query("""
                SELECT edge_response_result_type as name, COUNT(*) as count
                FROM cloudfront_logs
                WHERE timestamp BETWEEN ? AND ?
                  AND country = ?
                GROUP BY edge_response_result_type
                ORDER BY count DESC
                """,
                (rs, _) -> new NameCount(rs.getString("name"), rs.getLong(COUNT_FIELD)),
                from.toString(), to.toString(), countryCode);
    }

    public List<NameCount> countryUriStems(String countryCode, Instant from, Instant to, int limit) {
        String exclusionClause = excludedExtensions.stream()
                .map(_ -> "uri_stem NOT LIKE ?")
                .collect(Collectors.joining(" AND "));
        String sql = """
                SELECT CASE
                         WHEN uri_stem LIKE '/wp-%' THEN 'Wordpress'
                         WHEN uri_stem LIKE '%.php' THEN 'PHP'
                         ELSE uri_stem
                       END as name,
                       COUNT(*) as count
                FROM cloudfront_logs
                WHERE timestamp BETWEEN ? AND ?
                  AND country = ?
                """
                + (exclusionClause.isEmpty() ? "" : "  AND " + exclusionClause + "\n")
                + """
                GROUP BY name
                ORDER BY count DESC
                LIMIT ?
                """;

        var args = new ArrayList<>();
        args.add(from.toString());
        args.add(to.toString());
        args.add(countryCode);
        excludedExtensions.forEach(ext -> args.add("%." + ext.replaceFirst("^\\.", "")));
        args.add(limit);

        return jdbc.query(sql,
                (rs, _) -> new NameCount(rs.getString("name"), rs.getLong(COUNT_FIELD)),
                args.toArray());
    }

    public List<DailyResultTypeCount> countryRequestsPerDay(String countryCode, Instant from, Instant to) {
        return jdbc.query("""
                SELECT date(timestamp) as day,
                """ + RESULT_TYPE_SUMS + """

                FROM cloudfront_logs
                WHERE timestamp BETWEEN ? AND ?
                  AND country = ?
                GROUP BY day
                ORDER BY day
                """,
                (rs, _) -> new DailyResultTypeCount(
                        LocalDate.parse(rs.getString("day")),
                        rs.getLong("hit"),
                        rs.getLong("miss"),
                        rs.getLong(FIELD_FUNCTION),
                        rs.getLong(FIELD_ERROR),
                        rs.getLong(FIELD_REDIRECT)),
                from.toString(), to.toString(), countryCode);
    }

    public List<NameCount> topUriStems(Instant from, Instant to, int limit) {
        String exclusionClause = excludedExtensions.stream()
                .map(_ -> "uri_stem NOT LIKE ?")
                .collect(Collectors.joining(" AND "));
        String sql = """
                SELECT CASE
                         WHEN uri_stem LIKE '/wp-%' THEN 'Wordpress'
                         WHEN uri_stem LIKE '%.php' THEN 'PHP'
                         ELSE uri_stem
                       END as name,
                       COUNT(*) as count
                FROM cloudfront_logs
                WHERE timestamp BETWEEN ? AND ?
                """
                + (exclusionClause.isEmpty() ? "" : "  AND " + exclusionClause + "\n")
                + """
                GROUP BY name
                ORDER BY count DESC
                LIMIT ?
                """;

        var args = new ArrayList<>();
        args.add(from.toString());
        args.add(to.toString());
        excludedExtensions.forEach(ext -> args.add("%." + ext.replaceFirst("^\\.", "")));
        args.add(limit);

        return jdbc.query(sql,
                (rs, _) -> new NameCount(rs.getString("name"), rs.getLong(COUNT_FIELD)),
                args.toArray());
    }

    public List<NameCount> topAllowedUriStems(Instant from, Instant to, int limit) {
        String exclusionClause = excludedExtensions.stream()
                .map(_ -> "uri_stem NOT LIKE ?")
                .collect(Collectors.joining(" AND "));
        String sql = """
                SELECT uri_stem as name, COUNT(*) as count
                FROM cloudfront_logs
                WHERE timestamp BETWEEN ? AND ?
                  AND status < 400
                """
                + (exclusionClause.isEmpty() ? "" : "  AND " + exclusionClause + "\n")
                + """
                GROUP BY uri_stem
                ORDER BY count DESC
                LIMIT ?
                """;

        var args = new ArrayList<>();
        args.add(from.toString());
        args.add(to.toString());
        excludedExtensions.forEach(ext -> args.add("%." + ext.replaceFirst("^\\.", "")));
        args.add(limit);

        return jdbc.query(sql,
                (rs, _) -> new NameCount(rs.getString("name"), rs.getLong(COUNT_FIELD)),
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
        String selfExclusionClause = selfReferers.stream()
                .map(_ -> "referer NOT LIKE ?")
                .collect(Collectors.joining(" AND "));
        String sql = """
                SELECT referer as name, COUNT(*) as count
                FROM cloudfront_logs
                WHERE timestamp BETWEEN ? AND ?
                  AND referer IS NOT NULL
                """
                + (selfExclusionClause.isEmpty() ? "" : "  AND " + selfExclusionClause + "\n")
                + """
                GROUP BY referer
                ORDER BY count DESC
                LIMIT ?
                """;

        var args = new ArrayList<>();
        args.add(from.toString());
        args.add(to.toString());
        selfReferers.forEach(prefix -> args.add(prefix + "%"));
        args.add(limit);

        return jdbc.query(sql,
                (rs, _) -> new NameCount(rs.getString("name"), rs.getLong(COUNT_FIELD)),
                args.toArray());
    }

    public List<NameCount> uaResultTypes(String uaName, Instant from, Instant to) {
        return jdbc.query("""
                SELECT edge_response_result_type as name, COUNT(*) as count
                FROM cloudfront_logs
                WHERE timestamp BETWEEN ? AND ?
                  AND ua_name = ?
                GROUP BY edge_response_result_type
                ORDER BY count DESC
                """,
                (rs, _) -> new NameCount(rs.getString("name"), rs.getLong(COUNT_FIELD)),
                from.toString(), to.toString(), uaName);
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

    public List<NameCount> uaUriStems(String uaName, Instant from, Instant to, int limit) {
        String exclusionClause = excludedExtensions.stream()
                .map(_ -> "uri_stem NOT LIKE ?")
                .collect(Collectors.joining(" AND "));
        String sql = """
                SELECT CASE
                         WHEN uri_stem LIKE '/wp-%' THEN 'Wordpress'
                         WHEN uri_stem LIKE '%.php' THEN 'PHP'
                         ELSE uri_stem
                       END as name,
                       COUNT(*) as count
                FROM cloudfront_logs
                WHERE timestamp BETWEEN ? AND ?
                  AND ua_name = ?
                """
                + (exclusionClause.isEmpty() ? "" : "  AND " + exclusionClause + "\n")
                + """
                GROUP BY name
                ORDER BY count DESC
                LIMIT ?
                """;

        var args = new ArrayList<>();
        args.add(from.toString());
        args.add(to.toString());
        args.add(uaName);
        excludedExtensions.forEach(ext -> args.add("%." + ext.replaceFirst("^\\.", "")));
        args.add(limit);

        return jdbc.query(sql,
                (rs, _) -> new NameCount(rs.getString("name"), rs.getLong(COUNT_FIELD)),
                args.toArray());
    }

    public List<DailyResultTypeCount> uaRequestsPerDay(String uaName, Instant from, Instant to) {
        return jdbc.query("""
                SELECT date(timestamp) as day,
                """ + RESULT_TYPE_SUMS + """

                FROM cloudfront_logs
                WHERE timestamp BETWEEN ? AND ?
                  AND ua_name = ?
                GROUP BY day
                ORDER BY day
                """,
                (rs, _) -> new DailyResultTypeCount(
                        LocalDate.parse(rs.getString("day")),
                        rs.getLong("hit"),
                        rs.getLong("miss"),
                        rs.getLong(FIELD_FUNCTION),
                        rs.getLong(FIELD_ERROR),
                        rs.getLong(FIELD_REDIRECT)),
                from.toString(), to.toString(), uaName);
    }

    public List<DailyResultTypeCount> requestsPerDay(Instant from, Instant to) {
        return jdbc.query("""
                SELECT date(timestamp) as day,
                """ + RESULT_TYPE_SUMS + """

                FROM cloudfront_logs
                WHERE timestamp BETWEEN ? AND ?
                GROUP BY day
                ORDER BY day
                """,
                (rs, _) -> new DailyResultTypeCount(
                        LocalDate.parse(rs.getString("day")),
                        rs.getLong("hit"),
                        rs.getLong("miss"),
                        rs.getLong(FIELD_FUNCTION),
                        rs.getLong(FIELD_ERROR),
                        rs.getLong(FIELD_REDIRECT)),
                from.toString(), to.toString());
    }
}