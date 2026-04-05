package com.example.analyzelog.repository;

import com.example.analyzelog.model.CloudFrontLogEntry;
import com.example.analyzelog.service.UserAgentClassifier;
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
                timestamp, edge_location, sc_bytes, client_ip, method,
                uri_stem, status, referer, user_agent,
                edge_result_type, protocol, cs_bytes, time_taken,
                edge_response_result_type, protocol_version, time_to_first_byte,
                edge_detailed_result_type, content_type, content_length, country,
                ua_name
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

        try (PreparedStatement stmt = connection.prepareStatement(insertLog)) {
            for (CloudFrontLogEntry e : entries) {
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
                stmt.setString(11, e.protocol());
                stmt.setLong(12, e.csBytes());
                stmt.setDouble(13, e.timeTaken());
                stmt.setString(14, e.edgeResponseResultType());
                stmt.setString(15, e.protocolVersion());
                stmt.setDouble(16, e.timeToFirstByte());
                stmt.setString(17, e.edgeDetailedResultType());
                stmt.setString(18, e.contentType());
                setLongOrNull(stmt, 19, e.contentLength());
                stmt.setString(20, e.country());
                stmt.setString(21, UserAgentClassifier.classify(e.userAgent()));
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
                       MAX(timestamp) as latest
                FROM cloudfront_logs
                """)) {
            if (rs.next()) {
                return new Stats(
                    rs.getLong("total"),
                    rs.getString("earliest"),
                    rs.getString("latest")
                );
            }
        }
        return new Stats(0, null, null);
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

    public record Stats(long totalEntries, String earliest, String latest) {}
}