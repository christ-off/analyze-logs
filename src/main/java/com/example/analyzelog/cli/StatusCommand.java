package com.example.analyzelog.cli;

import com.example.analyzelog.repository.LogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(
    name = "status",
    description = "Show database statistics",
    mixinStandardHelpOptions = true
)
public class StatusCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(StatusCommand.class);

    @Option(names = {"-d", "--db"}, defaultValue = "logs.db", description = "SQLite database file path (default: logs.db)")
    private String dbPath;

    @Override
    public Integer call() {
        try (var repo = new LogRepository(dbPath)) {
            var stats = repo.getStats();
            log.info("Database : {}", dbPath);
            log.info("Entries  : {}", stats.totalEntries());
            log.info("Earliest : {}", stats.earliest() != null ? stats.earliest() : "—");
            log.info("Latest   : {}", stats.latest() != null ? stats.latest() : "—");
            return 0;
        } catch (Exception e) {
            log.error("Status failed", e);
            return 1;
        }
    }
}
