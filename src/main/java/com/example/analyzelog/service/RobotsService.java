package com.example.analyzelog.service;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.DisobedientBot;
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

        String now = Instant.now().toString();
        replaceAgentTable("robots_disallowed", parseDisallowedAgents(body), now);
        replaceAgentTable("robots_named_agents", parseNamedAgents(body), now);
    }

    private void replaceAgentTable(String table, List<String> agents, String refreshedAt) {
        jdbc.update("DELETE FROM " + table);
        for (String ua : agents) {
            jdbc.update("INSERT INTO " + table + " (user_agent, refreshed_at) VALUES (?, ?)", ua, refreshedAt);
        }
    }

    static List<String> parseDisallowedAgents(String robotsTxt) {
        return parseAgents(robotsTxt, true);
    }

    // Every agent named in robots.txt, regardless of whether it carries a Disallow rule —
    // used to recognize bots that check robots.txt in good faith before requesting anything else.
    static List<String> parseNamedAgents(String robotsTxt) {
        return parseAgents(robotsTxt, false);
    }

    private static List<String> parseAgents(String robotsTxt, boolean requireDisallow) {
        if (robotsTxt == null || robotsTxt.isBlank()) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String block : robotsTxt.split("\\r?\\n\\s*\\r?\\n")) {
            collectAgents(block, requireDisallow, result);
        }
        return new ArrayList<>(result);
    }

    private static void collectAgents(String block, boolean requireDisallow, Set<String> result) {
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
        if (!requireDisallow || hasDisallow) {
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