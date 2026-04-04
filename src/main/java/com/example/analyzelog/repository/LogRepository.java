package com.example.analyzelog.repository;

import com.example.analyzelog.model.CloudFrontLogEntry;
import liquibase.Liquibase;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;

import java.sql.*;
import java.time.ZonedDateTime;
import java.util.List;

public class LogRepository implements AutoCloseable {

    private final Connection connection;

    public LogRepository(String dbPath) throws SQLException {
        runMigrations(dbPath);
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        connection.setAutoCommit(false);
    }

    private static void runMigrations(String dbPath) throws SQLException {
        try (Connection migrationConn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(migrationConn));
            try (Liquibase liquibase = new Liquibase(
                    "db/changelog/db.changelog-master.xml",
                    new ClassLoaderResourceAccessor(),
                    database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        } catch (LiquibaseException e) {
            throw new SQLException("Database migration failed", e);
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
                edge_result_type, x_host_header, protocol, cs_bytes,
                time_taken, x_forwarded_for, ssl_protocol, ssl_cipher,
                edge_response_result_type, protocol_version, fle_status,
                fle_encrypted_fields, client_port, time_to_first_byte,
                edge_detailed_result_type, content_type, content_length,
                range_start, range_end, country
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
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
                stmt.setString(14, e.xHostHeader());
                stmt.setString(15, e.protocol());
                stmt.setLong(16, e.csBytes());
                stmt.setDouble(17, e.timeTaken());
                stmt.setString(18, e.xForwardedFor());
                stmt.setString(19, e.sslProtocol());
                stmt.setString(20, e.sslCipher());
                stmt.setString(21, e.edgeResponseResultType());
                stmt.setString(22, e.protocolVersion());
                stmt.setString(23, e.fleStatus());
                setIntOrNull(stmt, 24, e.fleEncryptedFields());
                stmt.setInt(25, e.clientPort());
                stmt.setDouble(26, e.timeToFirstByte());
                stmt.setString(27, e.edgeDetailedResultType());
                stmt.setString(28, e.contentType());
                setLongOrNull(stmt, 29, e.contentLength());
                setLongOrNull(stmt, 30, e.rangeStart());
                setLongOrNull(stmt, 31, e.rangeEnd());
                stmt.setString(32, e.country());
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