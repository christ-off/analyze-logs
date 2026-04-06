package com.example.analyzelog.service;

import com.example.analyzelog.model.DailyResultTypeCount;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.model.NameResultTypeCount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class DashboardService {

    private static final String COUNT_FIELD = "count";
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

    public DashboardService(JdbcTemplate jdbc, EdgeLocationResolver edgeLocationResolver) {
        this.jdbc = jdbc;
        this.edgeLocationResolver = edgeLocationResolver;
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
                        rs.getLong("function"),
                        rs.getLong("error"),
                        rs.getLong("redirect")),
                from.toString(), to.toString(), limit);
    }

    public List<NameCount> topBlockedCountries(Instant from, Instant to, int limit) {
        return jdbc.query("""
                SELECT country as name, COUNT(*) as count
                FROM cloudfront_logs
                WHERE timestamp BETWEEN ? AND ?
                  AND status = 403
                GROUP BY country
                ORDER BY count DESC
                LIMIT ?
                """,
                (rs, _) -> {
                    String iso = rs.getString("name");
                    String display = (iso != null && !iso.isBlank())
                            ? Locale.of("", iso).getDisplayCountry(Locale.ENGLISH)
                            : iso;
                    String label = (display != null && !display.equals(iso)) ? display : iso;
                    return new NameCount(label, rs.getLong(COUNT_FIELD));
                },
                from.toString(), to.toString(), limit);
    }

    public List<NameCount> topAllowedUriStems(Instant from, Instant to, int limit) {
        return jdbc.query("""
                SELECT uri_stem as name, COUNT(*) as count
                FROM cloudfront_logs
                WHERE timestamp BETWEEN ? AND ?
                  AND status < 400
                GROUP BY uri_stem
                ORDER BY count DESC
                LIMIT ?
                """,
                (rs, _) -> new NameCount(rs.getString("name"), rs.getLong(COUNT_FIELD)),
                from.toString(), to.toString(), limit);
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
                        rs.getLong("function"),
                        rs.getLong("error"),
                        rs.getLong("redirect")),
                from.toString(), to.toString());
    }
}