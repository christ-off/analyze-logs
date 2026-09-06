package com.example.analyzelog.service;

import com.example.analyzelog.model.CloudFrontLogEntry;
import com.example.analyzelog.model.DisobedientBot;
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