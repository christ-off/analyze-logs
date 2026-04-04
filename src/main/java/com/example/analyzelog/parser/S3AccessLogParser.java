package com.example.analyzelog.parser;

import com.example.analyzelog.model.AccessLogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Amazon S3 Server Access Log format.
 * Format: https://docs.aws.amazon.com/AmazonS3/latest/userguide/LogFormat.html
 */
public class S3AccessLogParser {

    private static final Logger log = LoggerFactory.getLogger(S3AccessLogParser.class);

    // Groups: owner bucket [time] ip requester requestId operation key "uri" status error bytes objSize totalTime turnaround "referrer" "userAgent" versionId ...
    private static final Pattern LOG_PATTERN = Pattern.compile(
        "(\\S+) (\\S+) \\[([^\\]]+)] (\\S+) (\\S+) (\\S+) (\\S+) (\\S+) " +
        "\"([^\"]*)\" (\\S+) (\\S+) (\\S+) (\\S+) (\\S+) (\\S+) " +
        "\"([^\"]*)\" \"([^\"]*)\" (\\S+).*"
    );

    private static final DateTimeFormatter TIME_FORMAT =
        DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);

    public List<AccessLogEntry> parse(String content) {
        var entries = new ArrayList<AccessLogEntry>();
        for (String line : content.lines().toList()) {
            if (line.isBlank()) continue;
            parseLine(line).ifPresent(entries::add);
        }
        return entries;
    }

    public Optional<AccessLogEntry> parseLine(String line) {
        Matcher m = LOG_PATTERN.matcher(line);
        if (!m.matches()) {
            log.warn("Unrecognized log line: {}", line);
            return Optional.empty();
        }

        try {
            return Optional.of(new AccessLogEntry(
                m.group(1),                        // bucketOwner
                m.group(2),                        // bucket
                ZonedDateTime.parse(m.group(3), TIME_FORMAT), // time
                m.group(4),                        // remoteIp
                nullIfDash(m.group(5)),            // requester
                m.group(6),                        // requestId
                m.group(7),                        // operation
                nullIfDash(m.group(8)),            // key
                nullIfDash(m.group(9)),            // requestUri
                parseLong(m.group(10)).map(Long::intValue).orElse(0), // httpStatus
                nullIfDash(m.group(11)),           // errorCode
                parseLong(m.group(12)).orElse(-1L), // bytesSent
                parseLong(m.group(13)).orElse(-1L), // objectSize
                parseLong(m.group(14)).orElse(-1L), // totalTime (ms)
                parseLong(m.group(15)).orElse(-1L), // turnAroundTime (ms)
                nullIfDash(m.group(16)),           // referrer
                nullIfDash(m.group(17)),           // userAgent
                nullIfDash(m.group(18))            // versionId
            ));
        } catch (Exception e) {
            log.warn("Failed to parse log line: {} — {}", line, e.getMessage());
            return Optional.empty();
        }
    }

    private static String nullIfDash(String value) {
        return "-".equals(value) ? null : value;
    }

    private static Optional<Long> parseLong(String value) {
        if ("-".equals(value)) return Optional.empty();
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
