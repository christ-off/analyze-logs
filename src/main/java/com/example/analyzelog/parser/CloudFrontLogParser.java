package com.example.analyzelog.parser;

import com.example.analyzelog.model.CloudFrontLogEntry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class CloudFrontLogParser {

    private static final Logger log = LoggerFactory.getLogger(CloudFrontLogParser.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    public List<CloudFrontLogEntry> parse(String content) {
        List<String> lines = content.lines().toList();
        boolean isTsv = lines.stream().anyMatch(l -> l.startsWith("#Fields:"));
        if (isTsv) {
            return parseTsvContent(lines);
        }
        var entries = new ArrayList<CloudFrontLogEntry>();
        for (String line : lines) {
            if (line.isBlank()) continue;
            parseLine(line).ifPresent(entries::add);
        }
        return entries;
    }

    private List<CloudFrontLogEntry> parseTsvContent(List<String> lines) {
        var entries = new ArrayList<CloudFrontLogEntry>();
        String[] fields = null;
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#Version:")) continue;
            if (line.startsWith("#Fields:")) {
                fields = line.substring(8).trim().split("\t");
                continue;
            }
            if (fields == null) continue;
            String[] values = line.split("\t", -1);
            var row = new HashMap<String, String>();
            for (int i = 0; i < fields.length && i < values.length; i++) {
                row.put(fields[i], values[i]);
            }
            parseRow(row).ifPresent(entries::add);
        }
        return entries;
    }

    public Optional<CloudFrontLogEntry> parseLine(String line) {
        try {
            JsonNode n = mapper.readTree(line);
            var row = new HashMap<String, String>();
            for (var entry : n.properties()) {
                row.put(entry.getKey(), entry.getValue().asString());
            }
            return parseRow(row);
        } catch (Exception e) {
            log.warn("Failed to parse line: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<CloudFrontLogEntry> parseRow(Map<String, String> row) {
        try {
            Instant timestamp = LocalDateTime
                .parse(row.get("date") + "T" + row.get("time"))
                .toInstant(ZoneOffset.UTC);

            if ("http".equals(row.get("cs-protocol"))) {
                return Optional.empty();
            }

            return Optional.of(new CloudFrontLogEntry(
                timestamp,
                text(row, "x-edge-location"),
                longVal(row, "sc-bytes"),
                text(row, "c-ip"),
                text(row, "cs-method"),
                text(row, "cs-uri-stem"),
                intVal(row, "sc-status"),
                nullIfDash(row, "cs(Referer)"),
                decodeUA(nullIfDash(row, "cs(User-Agent)")),
                text(row, "x-edge-result-type"),
                longVal(row, "cs-bytes"),
                doubleVal(row, "time-taken"),
                text(row, "x-edge-response-result-type"),
                text(row, "cs-protocol-version"),
                doubleVal(row, "time-to-first-byte"),
                text(row, "x-edge-detailed-result-type"),
                nullIfDash(row, "sc-content-type"),
                nullableLong(row, "sc-content-len"),
                text(row, "c-country")
            ));
        } catch (Exception e) {
            log.warn("Failed to parse row: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String text(Map<String, String> row, String field) {
        return row.get(field);
    }

    private static String decodeUA(String ua) {
        if (ua == null) return null;
        try {
            return URLDecoder.decode(ua, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException _) {
            return ua;
        }
    }

    private static String nullIfDash(Map<String, String> row, String field) {
        String v = text(row, field);
        return "-".equals(v) ? null : v;
    }

    private static int intVal(Map<String, String> row, String field) {
        String v = row.get(field);
        if (v == null || "-".equals(v)) return 0;
        try { return Integer.parseInt(v); }
        catch (NumberFormatException _) { return 0; }
    }

    private static long longVal(Map<String, String> row, String field) {
        String v = row.get(field);
        if (v == null || "-".equals(v)) return 0L;
        try { return Long.parseLong(v); }
        catch (NumberFormatException _) { return 0L; }
    }

    private static Long nullableLong(Map<String, String> row, String field) {
        String v = row.get(field);
        if (v == null || "-".equals(v)) return null;
        try { return Long.parseLong(v); }
        catch (NumberFormatException _) { return null; }
    }

    private static double doubleVal(Map<String, String> row, String field) {
        String v = row.get(field);
        if (v == null || "-".equals(v)) return 0.0;
        try { return Double.parseDouble(v); }
        catch (NumberFormatException _) { return 0.0; }
    }
}
