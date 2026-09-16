package com.example.analyzelog.service;

import com.example.analyzelog.model.CloudFrontLogEntry;
import com.example.analyzelog.model.DisobedientBot;
import com.example.analyzelog.model.ObedientBot;
import com.example.analyzelog.model.RobotsTxtSkippingBot;
import com.example.analyzelog.repository.LogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RobotsServiceTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("robots-test.db");
        registry.add("spring.datasource.url", () -> dbUrl);
        registry.add("app.db-path", () -> tempDir.resolve("robots-test.db").toString());
    }

    @Autowired
    RobotsService robotsService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    LogRepository repository;

    @BeforeEach
    void clearTable() {
        jdbc.update("DELETE FROM robots_disallowed");
        jdbc.update("DELETE FROM cloudfront_logs");
    }

    // ClaudeBot/1.0 classifies to ua_name="ClaudeBot", Googlebot/2.1 to "Googlebot"
    private static final String UA_CLAUDEBOT  = "ClaudeBot/1.0";
    private static final String UA_GOOGLEBOT  = "Googlebot/2.1";

    @Test
    void findDisobedientBots_returnsOnlyNonRobotsTxtRequests() {
        jdbc.update("INSERT INTO robots_disallowed (user_agent, refreshed_at) VALUES (?, ?)",
                "ClaudeBot", Instant.now().toString());

        Instant from = Instant.now();
        repository.saveEntries("logs/robots-disobedient-test.gz", List.of(
                entryWithUaAndUri(UA_CLAUDEBOT, "/robots.txt", "Hit"),     // must be excluded
                entryWithUaAndUri(UA_CLAUDEBOT, "/index.html", "Hit"),     // must be included
                entryWithUaAndUri(UA_CLAUDEBOT, "/about.html", "Miss")     // must be included
        ));

        List<DisobedientBot> result = robotsService.findDisobedientBots(from, Instant.now().plusSeconds(5));

        assertEquals(1, result.size());
        DisobedientBot bot = result.getFirst();
        assertEquals(UA_CLAUDEBOT, bot.userAgent());
        assertEquals(2, bot.count());
        assertEquals(1, bot.hit());
        assertEquals(1, bot.miss());
    }

    @Test
    void findDisobedientBots_emptyWhenNoData() {
        Instant from = Instant.now();
        List<DisobedientBot> result = robotsService.findDisobedientBots(from, Instant.now().plusSeconds(5));
        assertTrue(result.isEmpty());
    }

    @Test
    void findDisobedientBots_onlyMatchesRobotsDisallowedUaNames() {
        jdbc.update("INSERT INTO robots_disallowed (user_agent, refreshed_at) VALUES (?, ?)",
                "ClaudeBot", Instant.now().toString());

        Instant from = Instant.now();
        repository.saveEntries("logs/robots-known-test.gz", List.of(
                entryWithUaAndUri(UA_CLAUDEBOT,  "/index.html", "Hit"),
                entryWithUaAndUri(UA_GOOGLEBOT,  "/index.html", "Hit")    // not in robots_disallowed
        ));

        List<DisobedientBot> result = robotsService.findDisobedientBots(from, Instant.now().plusSeconds(5));

        assertEquals(1, result.size());
        assertEquals(UA_CLAUDEBOT, result.getFirst().userAgent());
    }

    @Test
    void findObedientBots_returnsOnlyBotsThatFetchedNothingButRobotsTxt() {
        jdbc.update("INSERT INTO robots_disallowed (user_agent, refreshed_at) VALUES (?, ?)",
                "ClaudeBot", Instant.now().toString());
        jdbc.update("INSERT INTO robots_disallowed (user_agent, refreshed_at) VALUES (?, ?)",
                "Googlebot", Instant.now().toString());

        Instant from = Instant.now();
        repository.saveEntries("logs/robots-obedient-test.gz", List.of(
                entryWithUaAndUri(UA_CLAUDEBOT, "/robots.txt", "Hit"),      // obedient: only robots.txt
                entryWithUaAndUri(UA_CLAUDEBOT, "/robots.txt", "Miss"),
                entryWithUaAndUri(UA_GOOGLEBOT, "/robots.txt", "Hit"),      // disobedient: also fetches other pages
                entryWithUaAndUri(UA_GOOGLEBOT, "/index.html", "Hit")
        ));

        List<ObedientBot> result = robotsService.findObedientBots(from, Instant.now().plusSeconds(5));

        assertEquals(1, result.size());
        ObedientBot bot = result.getFirst();
        assertEquals(UA_CLAUDEBOT, bot.userAgent());
        assertEquals(2, bot.count());
        assertEquals(1, bot.hit());
        assertEquals(1, bot.miss());
    }

    @Test
    void findObedientBots_emptyWhenNoData() {
        Instant from = Instant.now();
        List<ObedientBot> result = robotsService.findObedientBots(from, Instant.now().plusSeconds(5));
        assertTrue(result.isEmpty());
    }

    @Test
    void findBotsSkippingRobotsTxt_excludesBotsThatFetchedRobotsTxt() {
        Instant from = Instant.now();
        repository.saveEntries("logs/robots-skipped-test.gz", List.of(
                entryWithUaAndUri(UA_GOOGLEBOT, "/robots.txt", "Hit"),   // has robots.txt evidence: excluded
                entryWithUaAndUri(UA_GOOGLEBOT, "/index.html", "Hit"),
                entryWithUaAndUri(UA_CLAUDEBOT, "/index.html", "Hit"),   // no robots.txt evidence: included
                entryWithUaAndUri(UA_CLAUDEBOT, "/about.html", "Miss")
        ));

        List<RobotsTxtSkippingBot> result = robotsService.findBotsSkippingRobotsTxt(from, Instant.now().plusSeconds(5));

        assertEquals(1, result.size());
        RobotsTxtSkippingBot bot = result.getFirst();
        assertEquals(UA_CLAUDEBOT, bot.userAgent());
        assertEquals(2, bot.count());
        assertEquals(1, bot.hit());
        assertEquals(1, bot.miss());
    }

    @Test
    void findBotsSkippingRobotsTxt_groupsByRawUserAgentNotClassifiedName() {
        String claudeBotV2 = "ClaudeBot/2.0";

        Instant from = Instant.now();
        repository.saveEntries("logs/robots-skipped-variants-test.gz", List.of(
                entryWithUaAndUri(UA_CLAUDEBOT, "/index.html", "Hit"),
                entryWithUaAndUri(claudeBotV2, "/index.html", "Hit"),
                entryWithUaAndUri(claudeBotV2, "/about.html", "Hit")
        ));

        List<RobotsTxtSkippingBot> result = robotsService.findBotsSkippingRobotsTxt(from, Instant.now().plusSeconds(5));

        assertEquals(2, result.size());
        var byUa = result.stream().collect(java.util.stream.Collectors.toMap(RobotsTxtSkippingBot::userAgent, b -> b));
        assertEquals(1, byUa.get(UA_CLAUDEBOT).count());
        assertEquals(2, byUa.get(claudeBotV2).count());
    }

    @Test
    void findBotsSkippingRobotsTxt_checksRobotsTxtAcrossFullLogNotJustSelectedRange() {
        // Written before `from`, so it falls outside the queried range but is still in the full log.
        repository.saveEntries("logs/robots-skipped-history-test.gz", List.of(
                entryWithUaAndUri(UA_GOOGLEBOT, "/robots.txt", "Hit")
        ));

        Instant from = Instant.now();
        repository.saveEntries("logs/robots-skipped-recent-test.gz", List.of(
                entryWithUaAndUri(UA_GOOGLEBOT, "/index.html", "Hit")    // the only entry within [from, to]
        ));

        List<RobotsTxtSkippingBot> result = robotsService.findBotsSkippingRobotsTxt(from, Instant.now().plusSeconds(5));

        assertTrue(result.isEmpty(), "Googlebot's earlier robots.txt fetch should still exclude it");
    }

    @Test
    void findBotsSkippingRobotsTxt_onlyIncludesKnownBotGroups() {
        Instant from = Instant.now();
        repository.saveEntries("logs/robots-skipped-nonbot-test.gz", List.of(
                entryWithUaAndUri("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36", "/index.html", "Hit")
        ));

        List<RobotsTxtSkippingBot> result = robotsService.findBotsSkippingRobotsTxt(from, Instant.now().plusSeconds(5));

        assertTrue(result.isEmpty(), "non-bot UAs must not appear in the skipped-robots-txt listing");
    }

    @Test
    void findBotsSkippingRobotsTxt_emptyWhenNoData() {
        Instant from = Instant.now();
        List<RobotsTxtSkippingBot> result = robotsService.findBotsSkippingRobotsTxt(from, Instant.now().plusSeconds(5));
        assertTrue(result.isEmpty());
    }

    @Test
    void refresh_fetchesAndParsesLiveRobotsTxt() {
        robotsService.refresh();

        var refreshedAt = robotsService.getRefreshedAt();
        assertTrue(refreshedAt.isPresent(), "refreshed_at should be set after refresh");

        var count = jdbc.queryForObject("SELECT COUNT(*) FROM robots_disallowed", Long.class);
        assertTrue(count != null && count > 10, "robots_disallowed should have more than 10 entries after live fetch");
    }

    @Test
    void refresh_clearsTableBeforeReloading() {
        jdbc.update("INSERT INTO robots_disallowed (user_agent, refreshed_at) VALUES (?, ?)",
                "OldBot", "2020-01-01T00:00:00Z");

        robotsService.refresh();

        var remaining = jdbc.queryForList("SELECT user_agent FROM robots_disallowed WHERE user_agent = 'OldBot'", String.class);
        assertTrue(remaining.isEmpty(), "OldBot removed from stale entry should be gone after refresh");
    }

    @Test
    void getRefreshedAt_emptyWhenTableEmpty() {
        assertTrue(robotsService.getRefreshedAt().isEmpty());
    }

    @Test
    void getRefreshedAt_returnsMaxTimestamp() {
        jdbc.update("INSERT INTO robots_disallowed (user_agent, refreshed_at) VALUES (?, ?)",
                "BotA", "2026-01-01T00:00:00Z");
        jdbc.update("INSERT INTO robots_disallowed (user_agent, refreshed_at) VALUES (?, ?)",
                "BotB", "2026-06-01T00:00:00Z");

        var result = robotsService.getRefreshedAt();
        assertTrue(result.isPresent());
        assertEquals("2026-06-01T00:00:00Z", result.get());
    }

    private CloudFrontLogEntry entryWithUaAndUri(String ua, String uri, String resultType) {
        return new CloudFrontLogEntry(
                Instant.now(), "SFO53-P7", 1068L, "1.2.3.4", "GET",
                uri, 200,
                null, ua,
                resultType, 336L, 0.001,
                resultType, 0.001, resultType,
                null, null, "US"
        );
    }
}