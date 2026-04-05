package com.example.analyzelog.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public record DateRange(Instant from, Instant to) {

    public static DateRange lastDays(int n) {
        Instant to = Instant.now();
        Instant from = LocalDate.now().minusDays(n - 1L).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new DateRange(from, to);
    }

    public static DateRange lastMonths(int n) {
        Instant to = Instant.now();
        Instant from = LocalDate.now().minusMonths(n).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new DateRange(from, to);
    }

    public static DateRange fromParams(String fromStr, String toStr) {
        LocalDate from = LocalDate.parse(fromStr);
        LocalDate to = LocalDate.parse(toStr);
        if (from.isAfter(to)) throw new IllegalArgumentException("'from' must not be after 'to'");
        return new DateRange(
            from.atStartOfDay(ZoneOffset.UTC).toInstant(),
            to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        );
    }

    public String fromIso() {
        return from.toString();
    }

    public String toIso() {
        return to.toString();
    }

    public LocalDate fromDate() {
        return from.atZone(ZoneOffset.UTC).toLocalDate();
    }

    public LocalDate toDate() {
        // subtract 1s so that an end-of-range midnight (exclusive) maps back to the intended date
        return to.minusSeconds(1).atZone(ZoneOffset.UTC).toLocalDate();
    }
}