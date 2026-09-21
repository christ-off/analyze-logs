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

        List<RobotsRule> rules = parseRules(body);
        String now = TimestampFormat.sqlValue(Instant.now());
        jdbc.update("DELETE FROM robots_rules");
        for (RobotsRule rule : rules) {
            jdbc.update("INSERT INTO robots_rules (user_agent, path, refreshed_at) VALUES (?, ?, ?)",
                    rule.userAgent(), rule.path(), now);
        }
    }

    // path "" marks a group with nothing disallowed; "*" is the wildcard group.
    record RobotsRule(String userAgent, String path) {}

    static List<RobotsRule> parseRules(String robotsTxt) {
        if (robotsTxt == null || robotsTxt.isBlank()) return List.of();
        LinkedHashSet<RobotsRule> result = new LinkedHashSet<>();
        for (String block : robotsTxt.split("\\r?\\n\\s*\\r?\\n")) {
            collectRules(block, result);
        }
        return new ArrayList<>(result);
    }

    private static void collectRules(String block, Set<RobotsRule> result) {
        List<String> agents = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        for (String raw : block.lines().toList()) {
            String line = raw.trim();
            String lower = line.toLowerCase();
            if (lower.startsWith("user-agent:")) {
                agents.add(line.substring("user-agent:".length()).trim());
            } else if (lower.startsWith("disallow:")) {
                String path = line.substring("disallow:".length()).trim();
                if (!path.isEmpty()) paths.add(path);
            }
        }
        for (String agent : agents) {
            if (paths.isEmpty()) result.add(new RobotsRule(agent, ""));
            paths.forEach(p -> result.add(new RobotsRule(agent, p)));
        }
    }

    // A crawler follows its own named group if robots.txt has one, otherwise the "*" group.
    // Scope: known bots (static_ua) plus any agent named in robots.txt.
    private static final String BOT_SCOPE =
            "c.user_agent != ''\n" +
            "  AND c.timestamp BETWEEN ? AND ?\n" +
            "  AND (c.ua_name IN (SELECT ua_name FROM static_ua WHERE ua_group IN (" + DashboardService.BOT_UA_GROUPS_SQL_LIST + "))\n" +
            "       OR EXISTS (SELECT 1 FROM robots_rules n WHERE n.user_agent = c.ua_name))\n";

    private static final String VIOLATION = """
            (c.uri_stem != '/robots.txt' AND EXISTS (
                SELECT 1 FROM robots_rules r
                WHERE r.path != ''
                  AND substr(c.uri_stem, 1, length(r.path)) = r.path
                  AND r.user_agent = CASE WHEN EXISTS (SELECT 1 FROM robots_rules n WHERE n.user_agent = c.ua_name)
                                          THEN c.ua_name ELSE '*' END))""";

    private static final String COL_USER_AGENT = "user_agent";
    private static final String COL_COUNT = "count";
    private static final String COL_ERROR = "error";
    private static final String COL_FUNCTION = "function";

    public List<DisobedientBot> findDisobedientBots(Instant from, Instant to) {
        return jdbc.query(
                "SELECT c.user_agent,\n" +
                "       COUNT(*) AS count,\n" +
                ResultTypeSql.resultTypeSums("c") + "\n" +
                "FROM cloudfront_logs c\n" +
                "WHERE " + BOT_SCOPE +
                "  AND " + VIOLATION + "\n" +
                "GROUP BY c.user_agent\n" +
                "ORDER BY count DESC\n",
                (rs, _) -> new DisobedientBot(
                        rs.getString(COL_USER_AGENT),
                        rs.getLong(COL_COUNT),
                        rs.getLong("hit"),
                        rs.getLong("miss"),
                        rs.getLong(COL_ERROR),
                        rs.getLong(COL_FUNCTION)),
                TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to));
    }

    // count = other content requests (excluding /robots.txt); the hit/miss/... bar covers all requests.
    public List<ObedientBot> findObedientBots(Instant from, Instant to) {
        return jdbc.query(
                "SELECT c.user_agent,\n" +
                "       SUM(CASE WHEN c.uri_stem != '/robots.txt' THEN 1 ELSE 0 END) AS count,\n" +
                ResultTypeSql.resultTypeSums("c") + "\n" +
                "FROM cloudfront_logs c\n" +
                "WHERE " + BOT_SCOPE +
                "GROUP BY c.user_agent\n" +
                "HAVING SUM(" + VIOLATION + ") = 0\n" +
                "   AND SUM(CASE WHEN c.uri_stem = '/robots.txt' THEN 1 ELSE 0 END) > 0\n" +
                "ORDER BY count DESC\n",
                (rs, _) -> new ObedientBot(
                        rs.getString(COL_USER_AGENT),
                        rs.getLong(COL_COUNT),
                        rs.getLong("hit"),
                        rs.getLong("miss"),
                        rs.getLong(COL_ERROR),
                        rs.getLong(COL_FUNCTION)),
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
                "  AND s.ua_group IN (" + DashboardService.BOT_UA_GROUPS_SQL_LIST + ")\n" +
                "  AND r.ua_name IS NULL\n" +
                "GROUP BY c.user_agent\n" +
                "ORDER BY count DESC\n",
                (rs, _) -> new RobotsTxtSkippingBot(
                        rs.getString(COL_USER_AGENT),
                        rs.getLong(COL_COUNT),
                        rs.getLong("hit"),
                        rs.getLong("miss"),
                        rs.getLong(COL_ERROR),
                        rs.getLong(COL_FUNCTION)),
                TimestampFormat.sqlValue(from), TimestampFormat.sqlValue(to));
    }

    public Optional<String> getRefreshedAt() {
        return Optional.ofNullable(
                jdbc.queryForObject(
                        "SELECT MAX(refreshed_at) FROM robots_rules",
                        String.class));
    }
}