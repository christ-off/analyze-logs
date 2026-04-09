package com.example.analyzelog.repository;

import com.example.analyzelog.model.CloudFrontLogEntry;
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
            "Hit", "HTTP/1.1", 0.01, "Hit",
            null, null, "US"
        );

        assertDoesNotThrow(() -> repo.saveEntries("logs/nullable-test.gz", List.of(entry)));
    }

    private CloudFrontLogEntry entry(int status) {
        return new CloudFrontLogEntry(
            Instant.now(), "SFO53-P7", 1068L, "8.29.198.27", "GET",
            "/index.html", status,
            null, "TestAgent/1.0",
            "Hit", 336L, 0.001,
            "Hit", "HTTP/1.1", 0.001, "Hit",
            null, null, "US"
        );
    }
}