package com.example.analyzelog.service;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.DisobedientBot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("java:S2077") // SQL is fully parameterized — dynamic parts are trusted Java constants
@Service
public class RobotsService {

    private final JdbcTemplate jdbc;
    private final AppProperties appProperties;
    private final RestClient restClient = RestClient.create();

    public RobotsService(JdbcTemplate jdbc, AppProperties appProperties) {
        this.jdbc = jdbc;
        this.appProperties = appProperties;
    }

    public void refresh() {
        String body = restClient.get()
                .uri(appProperties.robotsUrl())
                .retrieve()
                .body(String.class);

        List<String> disallowed = parseDisallowedAgents(body);
        String now = Instant.now().toString();
        jdbc.update("DELETE FROM robots_disallowed");
        for (String ua : disallowed) {
            jdbc.update("INSERT INTO robots_disallowed (user_agent, refreshed_at) VALUES (?, ?)", ua, now);
        }
    }

    static List<String> parseDisallowedAgents(String robotsTxt) {
        if (robotsTxt == null || robotsTxt.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (String block : robotsTxt.split("\\r?\\n\\s*\\r?\\n")) {
            collectDisallowedAgents(block, result);
        }
        return result;
    }

    private static void collectDisallowedAgents(String block, List<String> result) {
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
                from.toString(), to.toString());
    }

    public Optional<String> getRefreshedAt() {
        return Optional.ofNullable(
                jdbc.queryForObject(
                        "SELECT MAX(refreshed_at) FROM robots_disallowed",
                        String.class));
    }
}