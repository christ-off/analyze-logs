package com.example.analyzelog.repository;

import com.example.analyzelog.model.CloudFrontLogEntry;
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
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LogRepositoryTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("test.db");
        registry.add("spring.datasource.url", () -> dbUrl);
        registry.add("app.db-path", () -> tempDir.resolve("test.db").toString());
    }

    @Autowired
    LogRepository repo;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void resetDb() {
        jdbc.execute("DELETE FROM cloudfront_logs");
        jdbc.execute("DELETE FROM fetched_files");
    }

    @Test
    void initialStatsAreEmpty() {
        var stats = repo.getStats();
        assertEquals(0, stats.totalEntries());
        assertNull(stats.earliest());
        assertNull(stats.latest());
    }

    @Test
    void savesAndCountsEntries() {
        var entries = List.of(entry(200), entry(404));
        repo.saveEntries("AWSLogs/123/CloudFront/dist.2026-01-01.gz", entries);

        var stats = repo.getStats();
        assertTrue(stats.totalEntries() >= 2);
    }

    @Test
    void tracksAlreadyFetchedFiles() {
        String key = "AWSLogs/123/CloudFront/dist.2026-01-02.gz";
        assertFalse(repo.isAlreadyFetched(key));

        repo.saveEntries(key, List.of(entry(200)));

        assertTrue(repo.isAlreadyFetched(key));
    }

    @Test
    void savesEmptyFileAndTracksIt() {
        String key = "AWSLogs/123/CloudFront/empty.gz";
        repo.saveEntries(key, List.of());

        assertTrue(repo.isAlreadyFetched(key));
    }

    @Test
    void handlesNullableFields() {
        var entry = new CloudFrontLogEntry(
            Instant.now(), "IAD89", 512L, "1.2.3.4", "GET",
            "/index.html", 200,
            null, null,
            "Hit", 128L, 0.01,
            "Hit", 0.01, "Hit",
            null, null, "US"
        );

        assertDoesNotThrow(() -> repo.saveEntries("logs/nullable-test.gz", List.of(entry)));
    }

    @Test
    void deletesOldLogs() {
        var oldEntry = new CloudFrontLogEntry(
            Instant.from(ZonedDateTime.now().minus(4, ChronoUnit.MONTHS)), "SFO53", 100L, "1.1.1.1", "GET",
            "/old", 200, null, "Bot",
            "Hit", 50L, 0.01, "Hit", 0.01, "Hit",
            null, null, "US"
        );
        repo.saveEntries("old.gz", List.of(oldEntry));

        var recentEntry = new CloudFrontLogEntry(
            Instant.from(ZonedDateTime.now().minus(1, ChronoUnit.MONTHS)), "SFO53", 100L, "1.1.1.1", "GET",
            "/recent", 200, null, "Bot",
            "Hit", 50L, 0.01, "Hit", 0.01, "Hit",
            null, null, "US"
        );
        repo.saveEntries("recent.gz", List.of(recentEntry));

        int deleted = repo.deleteOldLogs(3);

        assertEquals(1, deleted);
        var stats = repo.getStats();
        assertEquals(1, stats.totalEntries());
    }

    private CloudFrontLogEntry entry(int status) {
        return new CloudFrontLogEntry(
            Instant.now(), "SFO53-P7", 1068L, "8.29.198.27", "GET",
            "/index.html", status,
            null, "TestAgent/1.0",
            "Hit", 336L, 0.001,
            "Hit", 0.001, "Hit",
            null, null, "US"
        );
    }

}