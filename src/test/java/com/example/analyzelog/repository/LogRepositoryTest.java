package com.example.analyzelog.repository;

import com.example.analyzelog.model.AccessLogEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.ZonedDateTime;
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
        assertEquals(0, stats.buckets());
    }

    @Test
    void savesAndCountsEntries() throws SQLException {
        var entries = List.of(
            entry("mybucket", 200),
            entry("mybucket", 404)
        );

        repo.saveEntries("logs/2024-01-01-00-00-01-abc", entries);

        var stats = repo.getStats();
        assertEquals(2, stats.totalEntries());
        assertEquals(1, stats.buckets());
    }

    @Test
    void tracksAlreadyFetchedFiles() throws SQLException {
        String key = "logs/2024-01-01-file.log";
        assertFalse(repo.isAlreadyFetched(key));

        repo.saveEntries(key, List.of(entry("mybucket", 200)));

        assertTrue(repo.isAlreadyFetched(key));
    }

    @Test
    void savesEmptyFileAndTracksIt() throws SQLException {
        String key = "logs/empty.log";
        repo.saveEntries(key, List.of());

        assertTrue(repo.isAlreadyFetched(key));
        assertEquals(0, repo.getStats().totalEntries());
    }

    @Test
    void handlesMissingFields() throws SQLException {
        var entry = new AccessLogEntry(
            null, "mybucket", ZonedDateTime.now(),
            "10.0.0.1", null, "REQ001", "REST.GET.OBJECT",
            null, null, 403, "AccessDenied",
            -1L, -1L, -1L, -1L,
            null, null, null
        );

        assertDoesNotThrow(() -> repo.saveEntries("logs/test.log", List.of(entry)));
        assertEquals(1, repo.getStats().totalEntries());
    }

    private AccessLogEntry entry(String bucket, int status) {
        return new AccessLogEntry(
            "owner123", bucket, ZonedDateTime.now(),
            "1.2.3.4", "user", "REQ-" + status, "REST.GET.OBJECT",
            "test.jpg", "GET /test.jpg HTTP/1.1", status,
            null, 1024L, 1024L, 50L, 5L,
            "https://example.com/", "TestAgent/1.0", null
        );
    }
}
