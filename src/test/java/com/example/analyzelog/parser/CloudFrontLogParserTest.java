package com.example.analyzelog.parser;

import com.example.analyzelog.model.CloudFrontLogEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CloudFrontLogParserTest {

    private final CloudFrontLogParser parser = new CloudFrontLogParser();

    // All 33 standard fields present, most optional fields set to "-"
    private static final String SAMPLE_LINE = """
        {"date":"2026-04-04","time":"12:57:53","x-edge-location":"SFO53-P7","sc-bytes":"1068","c-ip":"8.29.198.27","cs-method":"GET","cs(Host)":"d3bkd4xdlxgkfz.cloudfront.net","cs-uri-stem":"/feed.xml","sc-status":"304","cs(Referer)":"-","cs(User-Agent)":"Feedly/1.0","cs-uri-query":"-","cs(Cookie)":"-","x-edge-result-type":"Hit","x-edge-request-id":"VqkomJQiWUvU0bQu33T0","x-host-header":"post-tenebras-lire.net","cs-protocol":"https","cs-bytes":"336","time-taken":"0.001","x-forwarded-for":"-","ssl-protocol":"TLSv1.3","ssl-cipher":"TLS_AES_128_GCM_SHA256","x-edge-response-result-type":"Hit","cs-protocol-version":"HTTP/1.1","fle-status":"-","fle-encrypted-fields":"-","c-port":"19103","time-to-first-byte":"0.001","x-edge-detailed-result-type":"Hit","sc-content-type":"-","sc-content-len":"-","sc-range-start":"-","sc-range-end":"-","c-country":"US"}
        """.strip();

    // Line with cookie, fle fields, range headers, large byte counts
    private static final String FULL_LINE = """
        {"date":"2026-01-01","time":"00:00:00","x-edge-location":"IAD89","sc-bytes":"5368709120","c-ip":"1.2.3.4","cs-method":"GET","cs(Host)":"abc.cloudfront.net","cs-uri-stem":"/video.mp4","sc-status":"206","cs(Referer)":"https://example.com","cs(User-Agent)":"curl/7.0","cs-uri-query":"q=test","cs(Cookie)":"session=abc123","x-edge-result-type":"Hit","x-edge-request-id":"REQ123","x-host-header":"example.com","cs-protocol":"https","cs-bytes":"256","time-taken":"2.500","x-forwarded-for":"203.0.113.5","ssl-protocol":"TLSv1.3","ssl-cipher":"TLS_AES_256_GCM_SHA384","x-edge-response-result-type":"Hit","cs-protocol-version":"HTTP/2.0","fle-status":"Processed","fle-encrypted-fields":"3","c-port":"443","time-to-first-byte":"0.050","x-edge-detailed-result-type":"Hit","sc-content-type":"video/mp4","sc-content-len":"1048576","sc-range-start":"0","sc-range-end":"1048575","c-country":"FR"}
        """.strip();

    @Test
    void parsesSampleLine() {
        Optional<CloudFrontLogEntry> result = parser.parseLine(SAMPLE_LINE);

        assertTrue(result.isPresent());
        CloudFrontLogEntry e = result.get();

        assertEquals(2026, e.timestamp().atZone(java.time.ZoneOffset.UTC).getYear());
        assertEquals(4, e.timestamp().atZone(java.time.ZoneOffset.UTC).getMonthValue());
        assertEquals(4, e.timestamp().atZone(java.time.ZoneOffset.UTC).getDayOfMonth());
        assertEquals(12, e.timestamp().atZone(java.time.ZoneOffset.UTC).getHour());
        assertEquals(57, e.timestamp().atZone(java.time.ZoneOffset.UTC).getMinute());

        assertEquals("SFO53-P7", e.edgeLocation());
        assertEquals(1068L, e.scBytes());
        assertEquals("8.29.198.27", e.clientIp());
        assertEquals("GET", e.method());
        assertEquals("/feed.xml", e.uriStem());
        assertEquals(304, e.status());
        assertNull(e.referer());
        assertEquals("Feedly/1.0", e.userAgent());
        assertEquals("Hit", e.edgeResultType());
        assertEquals(336L, e.csBytes());
        assertEquals(0.001, e.timeTaken(), 1e-6);
        assertEquals("Hit", e.edgeResponseResultType());
        assertEquals("HTTP/1.1", e.protocolVersion());
        assertEquals(0.001, e.timeToFirstByte(), 1e-6);
        assertEquals("Hit", e.edgeDetailedResultType());
        assertNull(e.contentType());
        assertNull(e.contentLength());
        assertEquals("US", e.country());
    }

    @Test
    void parsesFullLine() {
        Optional<CloudFrontLogEntry> result = parser.parseLine(FULL_LINE);

        assertTrue(result.isPresent());
        CloudFrontLogEntry e = result.get();

        assertEquals(5_368_709_120L, e.scBytes()); // >2GB, verifies long
        assertEquals("https://example.com", e.referer());
        assertEquals(206, e.status());
        assertEquals("video/mp4", e.contentType());
        assertEquals(1_048_576L, e.contentLength());
        assertEquals("HTTP/2.0", e.protocolVersion());
        assertEquals("FR", e.country());
    }

    @Test
    void parsesMultipleLines() {
        String content = SAMPLE_LINE + "\n" + FULL_LINE + "\n";
        List<CloudFrontLogEntry> entries = parser.parse(content);
        assertEquals(2, entries.size());
    }

    @Test
    void ignoresBlankLines() {
        String content = "\n" + SAMPLE_LINE + "\n\n";
        List<CloudFrontLogEntry> entries = parser.parse(content);
        assertEquals(1, entries.size());
    }

    @Test
    void decodesUrlEncodedUserAgent() {
        String line = SAMPLE_LINE.replace("\"Feedly/1.0\"", "\"Mozilla/5.0%20(compatible;%20Feedly/1.0)\"");
        Optional<CloudFrontLogEntry> result = parser.parseLine(line);
        assertTrue(result.isPresent());
        assertEquals("Mozilla/5.0 (compatible; Feedly/1.0)", result.get().userAgent());
    }

    @Test
    void returnsEmptyForGarbage() {
        assertTrue(parser.parseLine("this is not json").isEmpty());
        assertTrue(parser.parseLine("{}").isEmpty()); // missing required date/time
    }

    @Test
    void dashValuesProduceZeroOrNull() {
        // Replace numeric/optional fields with "-" to exercise dash-branch in intVal/longVal/doubleVal/nullableLong
        String line = SAMPLE_LINE
                .replace("\"sc-bytes\":\"1068\"",       "\"sc-bytes\":\"-\"")
                .replace("\"cs-bytes\":\"336\"",         "\"cs-bytes\":\"-\"")
                .replace("\"time-taken\":\"0.001\"",     "\"time-taken\":\"-\"")
                .replace("\"time-to-first-byte\":\"0.001\"", "\"time-to-first-byte\":\"-\"");

        Optional<CloudFrontLogEntry> result = parser.parseLine(line);
        assertTrue(result.isPresent());
        CloudFrontLogEntry e = result.get();
        assertEquals(0L,  e.scBytes());
        assertEquals(0L,  e.csBytes());
        assertEquals(0.0, e.timeTaken(),       1e-9);
        assertEquals(0.0, e.timeToFirstByte(), 1e-9);
        assertNull(e.contentLength()); // sc-content-len was already "-" in SAMPLE_LINE
    }

    @Test
    void invalidNumericValuesProduceZeroOrNull() {
        // Non-numeric strings trigger the NumberFormatException catch branches
        String line = SAMPLE_LINE
                .replace("\"sc-status\":\"304\"",        "\"sc-status\":\"INVALID\"")
                .replace("\"sc-bytes\":\"1068\"",        "\"sc-bytes\":\"INVALID\"")
                .replace("\"cs-bytes\":\"336\"",         "\"cs-bytes\":\"INVALID\"")
                .replace("\"time-taken\":\"0.001\"",     "\"time-taken\":\"INVALID\"")
                .replace("\"time-to-first-byte\":\"0.001\"", "\"time-to-first-byte\":\"INVALID\"");

        Optional<CloudFrontLogEntry> result = parser.parseLine(line);
        assertTrue(result.isPresent());
        CloudFrontLogEntry e = result.get();
        assertEquals(0,   e.status());
        assertEquals(0L,  e.scBytes());
        assertEquals(0L,  e.csBytes());
        assertEquals(0.0, e.timeTaken(),       1e-9);
        assertEquals(0.0, e.timeToFirstByte(), 1e-9);
    }

    @Test
    void skipsHttpEntries() {
        String line = SAMPLE_LINE.replace("\"cs-protocol\":\"https\"", "\"cs-protocol\":\"http\"");
        assertTrue(parser.parseLine(line).isEmpty());
    }

    @Test
    void invalidPercentEncodingInUserAgentFallsBack() {
        // URLDecoder throws IllegalArgumentException for a trailing lone '%'
        String line = SAMPLE_LINE.replace("\"Feedly/1.0\"", "\"Bad%UA%\"");
        Optional<CloudFrontLogEntry> result = parser.parseLine(line);
        assertTrue(result.isPresent());
        assertEquals("Bad%UA%", result.get().userAgent());
    }
}