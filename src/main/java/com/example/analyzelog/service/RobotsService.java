package com.example.analyzelog.service;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.DisobedientBot;
import com.example.analyzelog.model.ObedientBot;
import com.example.analyzelog.model.RobotsTxtSkippingBot;
import com.example.analyzelog.util.TimestampFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@SuppressWarnings("java:S2077") // SQL is fully parameterized — dynamic parts are trusted Java constants
@Service
public class RobotsService {

    private final JdbcTemplate jdbc;
    private final AppProperties appProperties;
    private final RestClient restClient;

    public RobotsService(JdbcTemplate jdbc, AppProperties appProperties, RestClient restClient) {
        this.jdbc = jdbc;
        this.appProperties = appProperties;
        this.restClient = restClient;
    }

    public void refresh() {
        String body = restClient.get()
                .uri(appProperties.robotsUrl())
                .retrieve()
                .body(String.class);

        List<String> disallowed = parseDisallowedAgents(body);
        String now = TimestampFormat.sqlValue(Instant.now());
        jdbc.update("DELETE FROM robots_disallowed");
        for (String ua : disallowed) {
            jdbc.update("INSERT INTO robots_disallowed (user_agent, refreshed_at) VALUES (?, ?)", ua, now);
        }
    }

    static List<String> parseDisallowedAgents(String robotsTxt) {
        if (robotsTxt == null || robotsTxt.isBlank()) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String block : robotsTxt.split("\\r?\\n\\s*\\r?\\n")) {
            collectDisallowedAgents(block, result);
        }
        return new ArrayList<>(result);
    }

    private static void collectDisallowedAgents(String block, Set<String> result) {
        List<String> agents = new ArrayList<>();
        boolean hasDisallow = false;
        for (String raw : block.lines().toList()) {
            String line = raw.trim();
            if (line.startsWith("#") || line.isEmpty()) continue;
            if (line.toLowerCase().startsWith("user-agent:")) {
                agents.add(line.substring("user-agent:".length()).trim());
            } else if (line.toLowerCase().startsWith("disallow:") && !line.substring("disallow:".length()).trim().isEmpty()) {
                hasDisallow = true;
            }
        }
        if (hasDisallow) {
            agents.stream().filter(a -> !a.equals("*")).forEach(result::add);
        }
    }

    public List<DisobedientBot> findDisobedientBots(Instant from, Instant to) {
        return jdbc.query(
                "SELECT c.user_agent,\n" +
                "       COUNT(*) AS count,\n" +
                ResultTypeSql.resultTypeSums("c") + "\n" +
                "FROM cloudfront_logs c\n" +
                "INNER JOIN robots_disallowed r ON c.ua_name = r.user_agent\n" +
                "WHERE c.uri_stem != '/robots.txt'\n" +
                "  AND c.user_agent != ''\n" +
                "  AND c.timestamp BETWEEN ? AND ?\n" +
                "GROUP BY c.user_agent\n" +
                "ORDER BY count DESC\n",
                (rs, _) -> new DisobedientBot(
                        rs.getString("user_agent"),
                        rs.getLong("count"),
                        rs.getLong("hit"),
                        rs.getLong("miss"),
                        rs.getLong("error"),
                        rs.getLong("function")),
                TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to));
    }

    public List<ObedientBot> findObedientBots(Instant from, Instant to) {
        return jdbc.query(
                "SELECT c.user_agent,\n" +
                "       COUNT(*) AS count,\n" +
                ResultTypeSql.resultTypeSums("c") + "\n" +
                "FROM cloudfront_logs c\n" +
                "INNER JOIN robots_disallowed r ON c.ua_name = r.user_agent\n" +
                "WHERE c.user_agent != ''\n" +
                "  AND c.timestamp BETWEEN ? AND ?\n" +
                "GROUP BY c.user_agent\n" +
                "HAVING SUM(CASE WHEN c.uri_stem != '/robots.txt' THEN 1 ELSE 0 END) = 0\n" +
                "ORDER BY count DESC\n",
                (rs, _) -> new ObedientBot(
                        rs.getString("user_agent"),
                        rs.getLong("count"),
                        rs.getLong("hit"),
                        rs.getLong("miss"),
                        rs.getLong("error"),
                        rs.getLong("function")),
                TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to));
    }

    // Known bots (static_ua ua_group) active in [from, to], grouped by their raw user_agent string,
    // whose ua_name family never appears against /robots.txt anywhere in the full log — not just
    // within the selected range.
    public List<RobotsTxtSkippingBot> findBotsSkippingRobotsTxt(Instant from, Instant to) {
        return jdbc.query(
                "WITH robots_txt_fetchers AS (\n" +
                "    SELECT DISTINCT ua_name FROM cloudfront_logs WHERE uri_stem = '/robots.txt'\n" +
                ")\n" +
                "SELECT c.user_agent,\n" +
                "       COUNT(*) AS count,\n" +
                ResultTypeSql.resultTypeSums("c") + "\n" +
                "FROM cloudfront_logs c\n" +
                "INNER JOIN static_ua s ON c.ua_name = s.ua_name\n" +
                "LEFT JOIN robots_txt_fetchers r ON c.ua_name = r.ua_name\n" +
                "WHERE c.timestamp BETWEEN ? AND ?\n" +
                "  AND s.ua_group IN ('AI Bots','Search Bots','Other Bots')\n" +
                "  AND r.ua_name IS NULL\n" +
                "GROUP BY c.user_agent\n" +
                "ORDER BY count DESC\n",
                (rs, _) -> new RobotsTxtSkippingBot(
                        rs.getString("user_agent"),
                        rs.getLong("count"),
                        rs.getLong("hit"),
                        rs.getLong("miss"),
                        rs.getLong("error"),
                        rs.getLong("function")),
                TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to));
    }

    public Optional<String> getRefreshedAt() {
        return Optional.ofNullable(
                jdbc.queryForObject(
                        "SELECT MAX(refreshed_at) FROM robots_disallowed",
                        String.class));
    }
}