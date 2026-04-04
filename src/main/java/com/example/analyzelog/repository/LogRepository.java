package com.example.analyzelog.repository;

import com.example.analyzelog.model.AccessLogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.ZonedDateTime;
import java.util.List;

public class LogRepository implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LogRepository.class);

    private final Connection connection;

    public LogRepository(String dbPath) throws SQLException {
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        connection.setAutoCommit(false);
        initialize();
    }

    private void initialize() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS access_logs (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    bucket_owner    TEXT,
                    bucket          TEXT NOT NULL,
                    time            TEXT NOT NULL,
                    remote_ip       TEXT,
                    requester       TEXT,
                    request_id      TEXT,
                    operation       TEXT,
                    key             TEXT,
                    request_uri     TEXT,
                    http_status     INTEGER,
                    error_code      TEXT,
                    bytes_sent      INTEGER,
                    object_size     INTEGER,
                    total_time_ms   INTEGER,
                    turnaround_ms   INTEGER,
                    referrer        TEXT,
                    user_agent      TEXT,
                    version_id      TEXT
                )
                """);

            stmt.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_access_logs_time
                    ON access_logs (time)
                """);

            stmt.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_access_logs_bucket
                    ON access_logs (bucket)
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

    public void saveEntries(String s3Key, List<AccessLogEntry> entries) throws SQLException {
        String insertLog = """
            INSERT INTO access_logs (
                bucket_owner, bucket, time, remote_ip, requester, request_id,
                operation, key, request_uri, http_status, error_code,
                bytes_sent, object_size, total_time_ms, turnaround_ms,
                referrer, user_agent, version_id
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

        try (PreparedStatement stmt = connection.prepareStatement(insertLog)) {
            for (AccessLogEntry e : entries) {
                stmt.setString(1, e.bucketOwner());
                stmt.setString(2, e.bucket());
                stmt.setString(3, e.time().toString());
                stmt.setString(4, e.remoteIp());
                stmt.setString(5, e.requester());
                stmt.setString(6, e.requestId());
                stmt.setString(7, e.operation());
                stmt.setString(8, e.key());
                stmt.setString(9, e.requestUri());
                stmt.setInt(10, e.httpStatus());
                stmt.setString(11, e.errorCode());
                setLongOrNull(stmt, 12, e.bytesSent());
                setLongOrNull(stmt, 13, e.objectSize());
                setLongOrNull(stmt, 14, e.totalTime());
                setLongOrNull(stmt, 15, e.turnAroundTime());
                stmt.setString(16, e.referrer());
                stmt.setString(17, e.userAgent());
                stmt.setString(18, e.versionId());
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
                       MIN(time) as earliest,
                       MAX(time) as latest,
                       COUNT(DISTINCT bucket) as buckets
                FROM access_logs
                """)) {
            if (rs.next()) {
                return new Stats(
                    rs.getLong("total"),
                    rs.getString("earliest"),
                    rs.getString("latest"),
                    rs.getInt("buckets")
                );
            }
        }
        return new Stats(0, null, null, 0);
    }

    private static void setLongOrNull(PreparedStatement stmt, int index, long value) throws SQLException {
        if (value < 0) {
            stmt.setNull(index, Types.INTEGER);
        } else {
            stmt.setLong(index, value);
        }
    }

    @Override
    public void close() throws SQLException {
        if (!connection.isClosed()) {
            connection.close();
        }
    }

    public record Stats(long totalEntries, String earliest, String latest, int buckets) {}
}
