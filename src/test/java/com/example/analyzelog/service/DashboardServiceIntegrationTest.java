package com.example.analyzelog.service;

import com.example.analyzelog.model.CloudFrontLogEntry;
import com.example.analyzelog.model.DailyResultTypeCount;
import com.example.analyzelog.model.NameResultTypeCount;
import com.example.analyzelog.repository.LogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DashboardServiceIntegrationTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("dashboard-test.db");
        registry.add("spring.datasource.url", () -> dbUrl);
        registry.add("app.db-path", () -> tempDir.resolve("dashboard-test.db").toString());
    }

    @Autowired
    LogRepository repository;

    @Autowired
    DashboardService dashboardService;

    @Test
    void topEdgeLocations_resolvesToHumanReadable() {
        Instant from = Instant.now();
        repository.saveEntries("logs/edge-test.gz", List.of(
                entry("SFO53-P7"),
                entry("SFO53-P7"),
                entry("CDG55-P2")
        ));

        var result = dashboardService.topEdgeLocations(from, Instant.now().plusSeconds(5), 10);

        assertFalse(result.isEmpty());
        var top = result.getFirst();
        assertEquals("San Francisco, United States", top.name());
        assertEquals(2, top.count());
    }

    @Test
    void topEdgeLocations_secondEntryIsResolved() {
        Instant from = Instant.now();
        repository.saveEntries("logs/edge-test-2.gz", List.of(
                entry("CDG55-P2"),
                entry("LHR5-P1")
        ));

        var result = dashboardService.topEdgeLocations(from, Instant.now().plusSeconds(5), 10);

        var names = result.stream().map(nc -> nc.name()).toList();
        assertTrue(names.stream().anyMatch(n -> n.contains("Paris")));
        assertTrue(names.stream().anyMatch(n -> n.contains("London")));
    }

    @Test
    void requestsPerDay_countsPerResultType() {
        Instant from = Instant.now();
        repository.saveEntries("logs/result-type-test.gz", List.of(
                entryWithResultType("Hit"),
                entryWithResultType("Hit"),
                entryWithResultType("Miss"),
                entryWithResultType("Error"),
                entryWithResultType("Redirect"),
                entryWithResultType("FunctionExecutionError")
        ));

        List<DailyResultTypeCount> result = dashboardService.requestsPerDay(
                from, Instant.now().plusSeconds(5));

        assertFalse(result.isEmpty());
        DailyResultTypeCount today = result.getLast();
        assertEquals(2, today.hit());
        assertEquals(1, today.miss());
        assertEquals(1, today.error());
        assertEquals(1, today.redirect());
        assertEquals(1, today.function());
    }

    // Real UA strings — ua_name is populated by UserAgentClassifier at insert time
    private static final String UA_CHROME_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String UA_FIREFOX_LINUX =
            "Mozilla/5.0 (X11; Linux x86_64; rv:147.0) Gecko/20100101 Firefox/147.0";

    @Test
    void topUserAgentsByResultType_countsPerResultType() {
        Instant from = Instant.now();
        repository.saveEntries("logs/ua-split-test.gz", List.of(
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "Hit"),
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "Hit"),
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "Miss"),
                entryWithUaAndResultType(UA_FIREFOX_LINUX, "Hit"),
                entryWithUaAndResultType(UA_FIREFOX_LINUX, "Error")
        ));

        List<NameResultTypeCount> result = dashboardService.topUserAgentsByResultType(
                from, Instant.now().plusSeconds(5), 10);

        assertFalse(result.isEmpty());
        NameResultTypeCount chrome = result.stream()
                .filter(r -> "Chrome / Windows".equals(r.name()))
                .findFirst().orElseThrow();
        assertEquals(2, chrome.hit());
        assertEquals(1, chrome.miss());
        assertEquals(0, chrome.error());

        NameResultTypeCount firefox = result.stream()
                .filter(r -> "Firefox / Linux".equals(r.name()))
                .findFirst().orElseThrow();
        assertEquals(1, firefox.hit());
        assertEquals(1, firefox.error());
    }

    private CloudFrontLogEntry entry(String edgeLocation) {
        return new CloudFrontLogEntry(
                Instant.now(), edgeLocation, 1068L, "1.2.3.4", "GET",
                "/index.html", 200,
                null, "TestAgent/1.0",
                "Hit", "https", 336L, 0.001,
                "Hit", "HTTP/1.1", 0.001, "Hit",
                null, null, "US"
        );
    }

    private CloudFrontLogEntry entryWithResultType(String resultType) {
        return new CloudFrontLogEntry(
                Instant.now(), "SFO53-P7", 1068L, "1.2.3.4", "GET",
                "/index.html", 200,
                null, "TestAgent/1.0",
                resultType, "https", 336L, 0.001,
                resultType, "HTTP/1.1", 0.001, resultType,
                null, null, "US"
        );
    }

    private CloudFrontLogEntry entryWithUaAndResultType(String ua, String resultType) {
        return new CloudFrontLogEntry(
                Instant.now(), "SFO53-P7", 1068L, "1.2.3.4", "GET",
                "/index.html", 200,
                null, ua,
                resultType, "https", 336L, 0.001,
                resultType, "HTTP/1.1", 0.001, resultType,
                null, null, "US"
        );
    }
}
