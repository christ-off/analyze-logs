package com.example.analyzelog.cli;

import com.example.analyzelog.fetcher.S3LogFetcher;
import com.example.analyzelog.parser.S3AccessLogParser;
import com.example.analyzelog.repository.LogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

@Command(
    name = "fetch",
    description = "Fetch S3 access logs and store them in the local database",
    mixinStandardHelpOptions = true
)
public class FetchCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(FetchCommand.class);

    @Option(names = {"-b", "--bucket"}, required = true, description = "S3 bucket containing the access logs")
    private String bucket;

    @Option(names = {"-p", "--prefix"}, defaultValue = "", description = "S3 key prefix for log files (default: root)")
    private String prefix;

    @Option(names = {"-r", "--region"}, defaultValue = "us-east-1", description = "AWS region (default: us-east-1)")
    private String region;

    @Option(names = {"--profile"}, description = "AWS credentials profile name")
    private String profile;

    @Option(names = {"-d", "--db"}, defaultValue = "logs.db", description = "SQLite database file path (default: logs.db)")
    private String dbPath;

    @Option(names = {"--since"}, description = "Only fetch log files modified on or after this date (yyyy-MM-dd)")
    private LocalDate since;

    @Option(names = {"--skip-existing"}, defaultValue = "true", description = "Skip files already in the database (default: true)")
    private boolean skipExisting;

    @Override
    public Integer call() {
        Instant sinceInstant = since != null ? since.atStartOfDay(ZoneOffset.UTC).toInstant() : null;

        var parser = new S3AccessLogParser();
        var fetched = new AtomicInteger(0);
        var skipped = new AtomicInteger(0);
        var failed = new AtomicInteger(0);

        try (var fetcher = new S3LogFetcher(region, profile);
             var repo = new LogRepository(dbPath)) {

            System.out.printf("Scanning s3://%s/%s%n", bucket, prefix);

            fetcher.streamLogKeys(bucket, prefix, sinceInstant, key -> {
                try {
                    if (skipExisting && repo.isAlreadyFetched(key)) {
                        skipped.incrementAndGet();
                        return;
                    }

                    String content = fetcher.downloadLogFile(bucket, key);
                    var entries = parser.parse(content);

                    if (!entries.isEmpty()) {
                        repo.saveEntries(key, entries);
                        System.out.printf("  [+] %s — %d entries%n", key, entries.size());
                    } else {
                        repo.saveEntries(key, entries); // still mark as fetched
                        System.out.printf("  [-] %s — empty%n", key);
                    }
                    fetched.incrementAndGet();

                } catch (Exception e) {
                    log.error("Failed to process {}: {}", key, e.getMessage());
                    failed.incrementAndGet();
                }
            });

            System.out.printf("%nDone. Fetched: %d, Skipped: %d, Failed: %d%n",
                fetched.get(), skipped.get(), failed.get());

            var stats = repo.getStats();
            System.out.printf("Database: %d total entries, from %s to %s%n",
                stats.totalEntries(), stats.earliest(), stats.latest());

            return failed.get() > 0 ? 1 : 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            log.error("Fetch failed", e);
            return 2;
        }
    }
}
