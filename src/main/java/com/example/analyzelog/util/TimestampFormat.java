package com.example.analyzelog.util;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

// cloudfront_logs.timestamp is a TEXT column compared lexicographically (SQLite has no
// native instant type). Instant.toString() strips trailing-zero fractional digits (a whole
// second prints with no fraction at all, e.g. "...T10:00:00Z"), which sorts AFTER
// "...T10:00:00.500Z" as a string even though it's chronologically earlier — silently
// dropping rows from BETWEEN-bounded queries whenever a compared instant lands on a
// second/millisecond boundary. Always formatting with a fixed millisecond width keeps
// lexicographic order equal to chronological order.
public final class TimestampFormat {

    private static final DateTimeFormatter FIXED_WIDTH_MILLIS = new DateTimeFormatterBuilder()
            .appendInstant(3)
            .toFormatter();

    private TimestampFormat() {}

    public static String sqlValue(Instant instant) {
        return FIXED_WIDTH_MILLIS.format(instant);
    }
}
