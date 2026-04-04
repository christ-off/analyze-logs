package com.example.analyzelog.cli;

import com.example.analyzelog.repository.LogRepository;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(
    name = "status",
    description = "Show database statistics",
    mixinStandardHelpOptions = true
)
public class StatusCommand implements Callable<Integer> {

    @Option(names = {"-d", "--db"}, defaultValue = "logs.db", description = "SQLite database file path (default: logs.db)")
    private String dbPath;

    @Override
    public Integer call() {
        try (var repo = new LogRepository(dbPath)) {
            var stats = repo.getStats();
            System.out.printf("Database : %s%n", dbPath);
            System.out.printf("Entries  : %d%n", stats.totalEntries());
            System.out.printf("Earliest : %s%n", stats.earliest() != null ? stats.earliest() : "—");
            System.out.printf("Latest   : %s%n", stats.latest() != null ? stats.latest() : "—");
            System.out.printf("Buckets  : %d%n", stats.buckets());
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }
}
