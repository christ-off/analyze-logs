package com.example.analyzelog.repository;

import com.example.analyzelog.model.CloudFrontLogEntry;

import java.sql.*;
import java.time.ZonedDateTime;
import java.util.List;

public class LogRepository implements AutoCloseable {

    private final Connection connection;

    public LogRepository(String dbPath) throws SQLException {
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        connection.setAutoCommit(false);
        initialize();
    }

    private void initialize() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS cloudfront_logs (
                    id                          INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp                   TEXT NOT NULL,
                    edge_location               TEXT,
                    sc_bytes                    INTEGER,
                    client_ip                   TEXT,
                    method                      TEXT,
                    host                        TEXT,
                    uri_stem                    TEXT,
                    status                      INTEGER,
                    referer                     TEXT,
                    user_agent                  TEXT,
                    uri_query                   TEXT,
                    cookie                      TEXT,
                    edge_result_type            TEXT,
                    request_id                  TEXT,
                    x_host_header               TEXT,
                    protocol                    TEXT,
                    cs_bytes                    INTEGER,
                    time_taken                  REAL,
                    x_forwarded_for             TEXT,
                    ssl_protocol                TEXT,
                    ssl_cipher                  TEXT,
                    edge_response_result_type   TEXT,
                    protocol_version            TEXT,
                    fle_status                  TEXT,
                    fle_encrypted_fields        INTEGER,
                    client_port                 INTEGER,
                    time_to_first_byte          REAL,
                    edge_detailed_result_type   TEXT,
                    content_type                TEXT,
                    content_length              INTEGER,
                    range_start                 INTEGER,
                    range_end                   INTEGER,
                    country                     TEXT
                )
                """);

            stmt.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_cloudfront_logs_timestamp
                    ON cloudfront_logs (timestamp)
                """);

            stmt.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_cloudfront_logs_host
                    ON cloudfront_logs (x_host_header)
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS fetched_files (
                    s3_key      TEXT PRIMARY KEY,
                    fetched_at  TEXT NOT NULL
                )
                """);

            connection.commit();
        }
    }

    public boolean isAlreadyFetched(String s3Key) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
            "SELECT 1 FROM fetched_files WHERE s3_key = ?")) {
            stmt.setString(1, s3Key);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void saveEntries(String s3Key, List<CloudFrontLogEntry> entries) throws SQLException {
        String insertLog = """
            INSERT INTO cloudfront_logs (
                timestamp, edge_location, sc_bytes, client_ip, method, host,
                uri_stem, status, referer, user_agent, uri_query, cookie,
                edge_result_type, request_id, x_host_header, protocol, cs_bytes,
                time_taken, x_forwarded_for, ssl_protocol, ssl_cipher,
                edge_response_result_type, protocol_version, fle_status,
                fle_encrypted_fields, client_port, time_to_first_byte,
                edge_detailed_result_type, content_type, content_length,
                range_start, range_end, country
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

        try (PreparedStatement stmt = connection.prepareStatement(insertLog)) {
            for (CloudFrontLogEntry e : entries) {
                stmt.setString(1, e.timestamp().toString());
                stmt.setString(2, e.edgeLocation());
                stmt.setLong(3, e.scBytes());
                stmt.setString(4, e.clientIp());
                stmt.setString(5, e.method());
                stmt.setString(6, e.host());
                stmt.setString(7, e.uriStem());
                stmt.setInt(8, e.status());
                stmt.setString(9, e.referer());
                stmt.setString(10, e.userAgent());
                stmt.setString(11, e.uriQuery());
                stmt.setString(12, e.cookie());
                stmt.setString(13, e.edgeResultType());
                stmt.setString(14, e.requestId());
                stmt.setString(15, e.xHostHeader());
                stmt.setString(16, e.protocol());
                stmt.setLong(17, e.csBytes());
                stmt.setDouble(18, e.timeTaken());
                stmt.setString(19, e.xForwardedFor());
                stmt.setString(20, e.sslProtocol());
                stmt.setString(21, e.sslCipher());
                stmt.setString(22, e.edgeResponseResultType());
                stmt.setString(23, e.protocolVersion());
                stmt.setString(24, e.fleStatus());
                setIntOrNull(stmt, 25, e.fleEncryptedFields());
                stmt.setInt(26, e.clientPort());
                stmt.setDouble(27, e.timeToFirstByte());
                stmt.setString(28, e.edgeDetailedResultType());
                stmt.setString(29, e.contentType());
                setLongOrNull(stmt, 30, e.contentLength());
                setLongOrNull(stmt, 31, e.rangeStart());
                setLongOrNull(stmt, 32, e.rangeEnd());
                stmt.setString(33, e.country());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }

        try (PreparedStatement stmt = connection.prepareStatement(
            "INSERT OR IGNORE INTO fetched_files (s3_key, fetched_at) VALUES (?, ?)")) {
            stmt.setString(1, s3Key);
            stmt.setString(2, ZonedDateTime.now().toString());
            stmt.executeUpdate();
        }

        connection.commit();
    }

    public Stats getStats() throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("""
                SELECT COUNT(*) as total,
                       MIN(timestamp) as earliest,
                       MAX(timestamp) as latest,
                       COUNT(DISTINCT x_host_header) as distributions
                FROM cloudfront_logs
                """)) {
            if (rs.next()) {
                return new Stats(
                    rs.getLong("total"),
                    rs.getString("earliest"),
                    rs.getString("latest"),
                    rs.getInt("distributions")
                );
            }
        }
        return new Stats(0, null, null, 0);
    }

    private static void setIntOrNull(PreparedStatement stmt, int index, Integer value) throws SQLException {
        if (value == null) stmt.setNull(index, Types.INTEGER);
        else stmt.setInt(index, value);
    }

    private static void setLongOrNull(PreparedStatement stmt, int index, Long value) throws SQLException {
        if (value == null) stmt.setNull(index, Types.INTEGER);
        else stmt.setLong(index, value);
    }

    @Override
    public void close() throws SQLException {
        if (!connection.isClosed()) {
            connection.close();
        }
    }

    public record Stats(long totalEntries, String earliest, String latest, int distributions) {}
}