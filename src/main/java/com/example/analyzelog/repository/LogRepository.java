package com.example.analyzelog.repository;

import com.example.analyzelog.model.CloudFrontLogEntry;
import com.example.analyzelog.service.EdgeLocationResolver;
import com.example.analyzelog.service.ReloadableClassifierService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.time.ZonedDateTime;
import java.util.List;

@Repository
public class LogRepository {

    private final JdbcTemplate jdbc;
    private final ReloadableClassifierService classifier;
    private final EdgeLocationResolver edgeLocationResolver;

    public LogRepository(JdbcTemplate jdbc, ReloadableClassifierService classifier, EdgeLocationResolver edgeLocationResolver) {
        this.jdbc = jdbc;
        this.classifier = classifier;
        this.edgeLocationResolver = edgeLocationResolver;
    }

    public boolean isAlreadyFetched(String s3Key) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM fetched_files WHERE s3_key = ?",
                Integer.class, s3Key);
        return count != null && count > 0;
    }

    @Transactional
    public void saveEntries(String s3Key, List<CloudFrontLogEntry> entries) {
        if (!entries.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO cloudfront_logs (
                        timestamp, edge_location, sc_bytes, client_ip, method,
                        uri_stem, status, referer, user_agent,
                        edge_result_type, cs_bytes, time_taken,
                        edge_response_result_type, protocol_version, time_to_first_byte,
                        edge_detailed_result_type, content_type, content_length, country,
                        ua_name, edge_location_iata
                    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    entries,
                    entries.size(),
                    (stmt, e) -> {
                        stmt.setString(1, e.timestamp().toString());
                        stmt.setString(2, e.edgeLocation());
                        stmt.setLong(3, e.scBytes());
                        stmt.setString(4, e.clientIp());
                        stmt.setString(5, e.method());
                        stmt.setString(6, e.uriStem());
                        stmt.setInt(7, e.status());
                        stmt.setString(8, e.referer());
                        stmt.setString(9, e.userAgent());
                        stmt.setString(10, e.edgeResultType());
                        stmt.setLong(11, e.csBytes());
                        stmt.setDouble(12, e.timeTaken());
                        stmt.setString(13, e.edgeResponseResultType());
                        stmt.setString(14, e.protocolVersion());
                        stmt.setDouble(15, e.timeToFirstByte());
                        stmt.setString(16, e.edgeDetailedResultType());
                        stmt.setString(17, e.contentType());
                        if (e.contentLength() == null) stmt.setNull(18, Types.INTEGER);
                        else stmt.setLong(18, e.contentLength());
                        stmt.setString(19, e.country());
                        stmt.setString(20, classifier.classify(e.userAgent()));
                        stmt.setString(21, edgeLocationResolver.extractIata(e.edgeLocation()));
                    });
        }

        jdbc.update(
                "INSERT OR IGNORE INTO fetched_files (s3_key, fetched_at) VALUES (?, ?)",
                s3Key, ZonedDateTime.now().toString());
    }

    public Stats getStats() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) as total, MIN(timestamp) as earliest, MAX(timestamp) as latest
                FROM cloudfront_logs
                """,
                (rs, _) -> new Stats(
                        rs.getLong("total"),
                        rs.getString("earliest"),
                        rs.getString("latest")));
    }

    public record Stats(long totalEntries, String earliest, String latest) {}
}