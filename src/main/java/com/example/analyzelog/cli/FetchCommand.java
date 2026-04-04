package com.example.analyzelog.cli;

import com.example.analyzelog.fetcher.S3LogFetcher;
import com.example.analyzelog.parser.CloudFrontLogParser;
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

        var parser = new CloudFrontLogParser();
        var fetched = new AtomicInteger(0);
        var skipped = new AtomicInteger(0);
        var failed = new AtomicInteger(0);

        try (var fetcher = new S3LogFetcher(region, profile);
             var repo = new LogRepository(dbPath)) {

            log.info("Scanning s3://{}/{}", bucket, prefix);

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
                        log.info("[+] {} — {} entries", key, entries.size());
                    } else {
                        repo.saveEntries(key, entries); // still mark as fetched
                        log.warn("[-] {} — empty", key);
                    }
                    fetched.incrementAndGet();

                } catch (Exception e) {
                    log.error("Failed to process {}: {}", key, e.getMessage());
                    failed.incrementAndGet();
                }
            });

            log.info("Done. Fetched: {}, Skipped: {}, Failed: {}",
                fetched.get(), skipped.get(), failed.get());

            var stats = repo.getStats();
            log.info("Database: {} total entries, from {} to {}",
                stats.totalEntries(), stats.earliest(), stats.latest());

            return failed.get() > 0 ? 1 : 0;

        } catch (Exception e) {
            log.error("Fetch failed", e);
            return 2;
        }
    }
}
