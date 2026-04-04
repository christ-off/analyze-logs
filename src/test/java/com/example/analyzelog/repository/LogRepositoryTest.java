package com.example.analyzelog.repository;

import com.example.analyzelog.model.CloudFrontLogEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogRepositoryTest {

    @TempDir
    Path tempDir;

    private LogRepository repo;

    @BeforeEach
    void setUp() throws SQLException {
        repo = new LogRepository(tempDir.resolve("test.db").toString());
    }

    @AfterEach
    void tearDown() throws SQLException {
        repo.close();
    }

    @Test
    void initialStatsAreEmpty() throws SQLException {
        var stats = repo.getStats();
        assertEquals(0, stats.totalEntries());
        assertNull(stats.earliest());
        assertNull(stats.latest());
    }

    @Test
    void savesAndCountsEntries() throws SQLException {
        var entries = List.of(
            entry(200),
            entry(404)
        );

        repo.saveEntries("AWSLogs/123/CloudFront/dist.2026-01-01.gz", entries);

        var stats = repo.getStats();
        assertEquals(2, stats.totalEntries());
    }

    @Test
    void tracksAlreadyFetchedFiles() throws SQLException {
        String key = "AWSLogs/123/CloudFront/dist.2026-01-01.gz";
        assertFalse(repo.isAlreadyFetched(key));

        repo.saveEntries(key, List.of(entry(200)));

        assertTrue(repo.isAlreadyFetched(key));
    }

    @Test
    void savesEmptyFileAndTracksIt() throws SQLException {
        String key = "AWSLogs/123/CloudFront/empty.gz";
        repo.saveEntries(key, List.of());

        assertTrue(repo.isAlreadyFetched(key));
        assertEquals(0, repo.getStats().totalEntries());
    }

    @Test
    void handlesNullableFields() throws SQLException {
        var entry = new CloudFrontLogEntry(
            Instant.now(), "IAD89", 512L, "1.2.3.4", "GET",
            "/index.html", 200,
            null, null, null, null,
            "Hit", "https", 128L, 0.01,
            null, "TLSv1.3", "TLS_AES_128_GCM_SHA256", "Hit",
            "HTTP/1.1", null, null, 443, 0.01, "Hit",
            null, null, null, null, "US"
        );

        assertDoesNotThrow(() -> repo.saveEntries("logs/test.gz", List.of(entry)));
        assertEquals(1, repo.getStats().totalEntries());
    }

    private CloudFrontLogEntry entry(int status) {
        return new CloudFrontLogEntry(
            Instant.now(), "SFO53-P7", 1068L, "8.29.198.27", "GET",
            "/index.html", status,
            null, "TestAgent/1.0", null, null,
            "Hit", "https", 336L, 0.001,
            null, "TLSv1.3", "TLS_AES_128_GCM_SHA256", "Hit",
            "HTTP/1.1", null, null, 19103, 0.001, "Hit",
            null, null, null, null, "US"
        );
    }
}