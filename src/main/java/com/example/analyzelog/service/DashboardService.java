package com.example.analyzelog.service;

import com.example.analyzelog.model.DailyStatusCount;
import com.example.analyzelog.model.NameCount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class DashboardService {

    private static final String COUNT_FIELD = "count";
    private final JdbcTemplate jdbc;

    public DashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<NameCount> topUserAgents(Instant from, Instant to, int limit) {
        return jdbc.query("""
                SELECT ua_name as name, COUNT(*) as count
                FROM cloudfront_logs
                WHERE timestamp BETWEEN ? AND ?
                GROUP BY ua_name
                ORDER BY count DESC
                LIMIT ?
                """,
                (rs, _) -> new NameCount(rs.getString("name"), rs.getLong(COUNT_FIELD)),
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

    public List<DailyStatusCount> requestsPerDay(Instant from, Instant to) {
        return jdbc.query("""
                SELECT date(timestamp) as day,
                       SUM(CASE WHEN status >= 200 AND status < 400 THEN 1 ELSE 0 END) as success,
                       SUM(CASE WHEN status >= 400 AND status < 500 THEN 1 ELSE 0 END) as client_error,
                       SUM(CASE WHEN status >= 500               THEN 1 ELSE 0 END) as server_error
                FROM cloudfront_logs
                WHERE timestamp BETWEEN ? AND ?
                GROUP BY day
                ORDER BY day
                """,
                (rs, _) -> new DailyStatusCount(
                        LocalDate.parse(rs.getString("day")),
                        rs.getLong("success"),
                        rs.getLong("client_error"),
                        rs.getLong("server_error")),
                from.toString(), to.toString());
    }
}