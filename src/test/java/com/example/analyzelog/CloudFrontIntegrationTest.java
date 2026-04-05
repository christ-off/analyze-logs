package com.example.analyzelog;

import com.example.analyzelog.model.CloudFrontLogEntry;
import com.example.analyzelog.parser.CloudFrontLogParser;
import com.example.analyzelog.repository.LogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

class CloudFrontIntegrationTest {

    private static final String LOG_FILE = "/E1Z5N5X273TPT1.2026-04-04-12.27178cb4.gz";

    private final CloudFrontLogParser parser = new CloudFrontLogParser();

    @TempDir
    Path tempDir;

    private String decompress(String resource) throws IOException {
        try (InputStream raw = getClass().getResourceAsStream(resource);
             GZIPInputStream gzip = new GZIPInputStream(raw)) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parsesAllEntries() throws IOException {
        List<CloudFrontLogEntry> entries = parser.parse(decompress(LOG_FILE));

        assertEquals(3, entries.size());
    }

    @Test
    void firstEntryFields() throws IOException {
        CloudFrontLogEntry e = parser.parse(decompress(LOG_FILE)).getFirst();

        assertEquals(Instant.parse("2026-04-04T12:57:53Z"), e.timestamp());
        assertEquals("SFO53-P7", e.edgeLocation());
        assertEquals(1068L, e.scBytes());
        assertEquals("8.29.198.27", e.clientIp());
        assertEquals("GET", e.method());
        assertEquals("/feed.xml", e.uriStem());
        assertEquals(304, e.status());
        assertNull(e.referer());
        assertNull(e.uriQuery());
        assertEquals("Hit", e.edgeResultType());
        assertEquals("https", e.protocol());
        assertEquals(336L, e.csBytes());
        assertEquals(0.001, e.timeTaken(), 1e-6);
        assertEquals("HTTP/1.1", e.protocolVersion());
        assertEquals(0.001, e.timeToFirstByte(), 1e-6);
        assertEquals("Hit", e.edgeDetailedResultType());
        assertNull(e.contentType());
        assertNull(e.contentLength());
        assertEquals("US", e.country());
    }

    @Test
    void thirdEntryHasContentType() throws IOException {
        CloudFrontLogEntry e = parser.parse(decompress(LOG_FILE)).get(2);

        assertEquals("text/html", e.contentType());
        assertEquals(32510L, e.contentLength());
        assertEquals("Miss", e.edgeResultType());
        assertEquals("HEAD", e.method());
    }

    @Test
    void persistsToDatabase() throws IOException, SQLException {
        List<CloudFrontLogEntry> entries = parser.parse(decompress(LOG_FILE));
        String s3Key = "AWSLogs/424590257573/CloudFront/E1Z5N5X273TPT1.2026-04-04-12.27178cb4.gz";

        try (var repo = new LogRepository(tempDir.resolve("test.db").toString())) {
            repo.saveEntries(s3Key, entries);

            var stats = repo.getStats();
            assertEquals(3, stats.totalEntries());
            assertNotNull(stats.earliest());
            assertNotNull(stats.latest());
            assertTrue(repo.isAlreadyFetched(s3Key));
        }
    }
}