package com.example.analyzelog.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimestampFormatTest {

    @Test
    void alwaysEmitsThreeFractionalDigits() {
        assertEquals("2026-09-12T09:57:55.000Z", TimestampFormat.sqlValue(Instant.parse("2026-09-12T09:57:55Z")));
        assertEquals("2026-09-12T09:57:55.003Z", TimestampFormat.sqlValue(Instant.parse("2026-09-12T09:57:55.003Z")));
        assertEquals("2026-09-12T09:57:55.123Z", TimestampFormat.sqlValue(Instant.parse("2026-09-12T09:57:55.123456789Z")));
    }

    // Instant.toString() drops trailing-zero fractional digits, so a whole-second instant
    // ("...:55Z") sorts AFTER "...:55.003Z" as a plain string even though it's earlier —
    // silently dropping rows from a lexicographic BETWEEN query. The fixed-width format
    // must keep string order equal to chronological order across that boundary.
    @Test
    void fixedWidthPreservesChronologicalOrderAcrossSecondBoundary() {
        Instant onTheSecond = Instant.parse("2026-09-12T09:57:55Z");
        Instant threeMsLater = onTheSecond.plusMillis(3);

        assertTrue(onTheSecond.toString().compareTo(threeMsLater.toString()) > 0,
                "sanity check: Instant.toString() alone gets this backwards");

        assertTrue(TimestampFormat.sqlValue(onTheSecond).compareTo(TimestampFormat.sqlValue(threeMsLater)) < 0);
    }
}
