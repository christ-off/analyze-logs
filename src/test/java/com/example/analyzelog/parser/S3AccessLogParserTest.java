package com.example.analyzelog.parser;

import com.example.analyzelog.model.AccessLogEntry;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class S3AccessLogParserTest {

    private final S3AccessLogParser parser = new S3AccessLogParser();

    // Full example from AWS documentation
    private static final String SAMPLE_LINE =
        "79a59df900b949e55d96a1e698fbacedfd6e09d98eacf8f8d5218e7cd47ef2be mybucket " +
        "[06/Feb/2019:00:00:38 +0000] 192.0.2.3 - 3E57427F3EXAMPLE REST.GET.OBJECT " +
        "photos/2019/08/puppies.jpg \"GET /mybucket/photos/2019/08/puppies.jpg HTTP/1.1\" " +
        "200 - 853 853 41 40 \"https://bucket.s3.amazonaws.com/\" " +
        "\"Mozilla/5.0 (compatible)\" - qqfMCss= TLSv1.2 ECDHE-RSA-AES128-GCM-SHA256 " +
        "AuthHeader mybucket.s3.amazonaws.com - -";

    private static final String DASH_FIELDS_LINE =
        "abc123 mybucket [01/Jan/2024:12:00:00 +0000] 10.0.0.1 - REQ001 REST.GET.OBJECT " +
        "- \"-\" 403 AccessDenied - - 15 14 \"-\" \"-\" -";

    @Test
    void parsesFullLine() {
        Optional<AccessLogEntry> result = parser.parseLine(SAMPLE_LINE);

        assertTrue(result.isPresent());
        AccessLogEntry entry = result.get();

        assertEquals("79a59df900b949e55d96a1e698fbacedfd6e09d98eacf8f8d5218e7cd47ef2be", entry.bucketOwner());
        assertEquals("mybucket", entry.bucket());
        assertEquals(2019, entry.time().getYear());
        assertEquals(2, entry.time().getMonthValue());
        assertEquals(6, entry.time().getDayOfMonth());
        assertEquals(0, entry.time().getHour());
        assertEquals("192.0.2.3", entry.remoteIp());
        assertNull(entry.requester());
        assertEquals("3E57427F3EXAMPLE", entry.requestId());
        assertEquals("REST.GET.OBJECT", entry.operation());
        assertEquals("photos/2019/08/puppies.jpg", entry.key());
        assertEquals("GET /mybucket/photos/2019/08/puppies.jpg HTTP/1.1", entry.requestUri());
        assertEquals(200, entry.httpStatus());
        assertNull(entry.errorCode());
        assertEquals(853L, entry.bytesSent());
        assertEquals(853L, entry.objectSize());
        assertEquals(41L, entry.totalTime());
        assertEquals(40L, entry.turnAroundTime());
        assertEquals("https://bucket.s3.amazonaws.com/", entry.referrer());
        assertEquals("Mozilla/5.0 (compatible)", entry.userAgent());
        assertNull(entry.versionId());
    }

    @Test
    void parsesDashFieldsAsNull() {
        Optional<AccessLogEntry> result = parser.parseLine(DASH_FIELDS_LINE);

        assertTrue(result.isPresent());
        AccessLogEntry entry = result.get();

        assertNull(entry.key());
        assertNull(entry.requestUri());
        assertEquals(403, entry.httpStatus());
        assertEquals("AccessDenied", entry.errorCode());
        assertEquals(-1L, entry.bytesSent());
        assertEquals(-1L, entry.objectSize());
        assertNull(entry.referrer());
        assertNull(entry.userAgent());
        assertNull(entry.versionId());
    }

    @Test
    void parsesMultipleLines() {
        String content = SAMPLE_LINE + "\n" + DASH_FIELDS_LINE + "\n";
        List<AccessLogEntry> entries = parser.parse(content);
        assertEquals(2, entries.size());
    }

    @Test
    void ignoresBlankLines() {
        String content = "\n" + SAMPLE_LINE + "\n\n";
        List<AccessLogEntry> entries = parser.parse(content);
        assertEquals(1, entries.size());
    }

    @Test
    void returnsEmptyForGarbageLine() {
        Optional<AccessLogEntry> result = parser.parseLine("this is not a valid log line");
        assertTrue(result.isEmpty());
    }

    @Test
    void parsesTimezone() {
        Optional<AccessLogEntry> result = parser.parseLine(SAMPLE_LINE);
        assertTrue(result.isPresent());
        ZonedDateTime time = result.get().time();
        assertEquals(0, time.getOffset().getTotalSeconds()); // UTC+0
    }
}
