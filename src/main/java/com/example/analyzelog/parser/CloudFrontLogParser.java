package com.example.analyzelog.parser;

import com.example.analyzelog.model.CloudFrontLogEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CloudFrontLogParser {

    private static final Logger log = LoggerFactory.getLogger(CloudFrontLogParser.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    public List<CloudFrontLogEntry> parse(String content) {
        var entries = new ArrayList<CloudFrontLogEntry>();
        for (String line : content.lines().toList()) {
            if (line.isBlank()) continue;
            parseLine(line).ifPresent(entries::add);
        }
        return entries;
    }

    public Optional<CloudFrontLogEntry> parseLine(String line) {
        try {
            JsonNode n = mapper.readTree(line);
            Instant timestamp = LocalDateTime
                .parse(n.get("date").asText() + "T" + n.get("time").asText())
                .toInstant(ZoneOffset.UTC);

            return Optional.of(new CloudFrontLogEntry(
                timestamp,
                text(n, "x-edge-location"),
                longVal(n, "sc-bytes"),
                text(n, "c-ip"),
                text(n, "cs-method"),
                text(n, "cs(Host)"),
                text(n, "cs-uri-stem"),
                intVal(n, "sc-status"),
                nullIfDash(n, "cs(Referer)"),
                nullIfDash(n, "cs(User-Agent)"),
                nullIfDash(n, "cs-uri-query"),
                nullIfDash(n, "cs(Cookie)"),
                text(n, "x-edge-result-type"),
                text(n, "x-host-header"),
                text(n, "cs-protocol"),
                longVal(n, "cs-bytes"),
                doubleVal(n, "time-taken"),
                nullIfDash(n, "x-forwarded-for"),
                nullIfDash(n, "ssl-protocol"),
                nullIfDash(n, "ssl-cipher"),
                text(n, "x-edge-response-result-type"),
                text(n, "cs-protocol-version"),
                nullIfDash(n, "fle-status"),
                nullableInt(n, "fle-encrypted-fields"),
                intVal(n, "c-port"),
                doubleVal(n, "time-to-first-byte"),
                text(n, "x-edge-detailed-result-type"),
                nullIfDash(n, "sc-content-type"),
                nullableLong(n, "sc-content-len"),
                nullableLong(n, "sc-range-start"),
                nullableLong(n, "sc-range-end"),
                text(n, "c-country")
            ));
        } catch (Exception e) {
            log.warn("Failed to parse line: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v != null ? v.asText() : null;
    }

    private static String nullIfDash(JsonNode n, String field) {
        String v = text(n, field);
        return "-".equals(v) ? null : v;
    }

    private static int intVal(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || "-".equals(v.asText())) return 0;
        try { return Integer.parseInt(v.asText()); }
        catch (NumberFormatException _) { return 0; }
    }

    private static long longVal(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || "-".equals(v.asText())) return 0L;
        try { return Long.parseLong(v.asText()); }
        catch (NumberFormatException _) { return 0L; }
    }

    private static Integer nullableInt(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || "-".equals(v.asText())) return null;
        try { return Integer.parseInt(v.asText()); }
        catch (NumberFormatException _) { return null; }
    }

    private static Long nullableLong(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || "-".equals(v.asText())) return null;
        try { return Long.parseLong(v.asText()); }
        catch (NumberFormatException _) { return null; }
    }

    private static double doubleVal(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || "-".equals(v.asText())) return 0.0;
        try { return Double.parseDouble(v.asText()); }
        catch (NumberFormatException _) { return 0.0; }
    }
}
