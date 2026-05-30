package com.example.analyzelog.service;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.fetcher.S3LogFetcher;
import com.example.analyzelog.parser.CloudFrontLogParser;
import com.example.analyzelog.repository.LogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class FetchService {

    private static final Logger log = LoggerFactory.getLogger(FetchService.class);

    private final S3LogFetcher fetcher;
    private final CloudFrontLogParser parser;
    private final LogRepository repository;
    private final AppProperties props;

    private final AtomicReference<FetchProgress> progress =
            new AtomicReference<>(FetchProgress.idle());

    public FetchService(S3LogFetcher fetcher, CloudFrontLogParser parser,
                        LogRepository repository, AppProperties props) {
        this.fetcher = fetcher;
        this.parser = parser;
        this.repository = repository;
        this.props = props;
    }

    public record FetchResult(int fetched, int skipped, int failed) {}

    public record FetchProgress(
            int total, int processed,
            int fetched, int skipped, int failed,
            boolean done, String error) {

        public static FetchProgress idle() {
            return new FetchProgress(0, 0, 0, 0, 0, false, null);
        }
    }

    public FetchProgress getProgress() {
        return progress.get();
    }

    public FetchResult fetch(LocalDate since, boolean skipExisting) {
        String bucket = props.aws().bucket();
        String prefix = props.aws().prefix();
        Instant sinceInstant = since != null ? since.atStartOfDay(ZoneOffset.UTC).toInstant() : null;

        List<String> keys = new ArrayList<>();
        fetcher.streamLogKeys(bucket, prefix, sinceInstant, keys::add);
        int total = keys.size();
        progress.set(new FetchProgress(total, 0, 0, 0, 0, false, null));

        int fetched = 0;
        int skipped = 0;
        int failed = 0;

        for (String key : keys) {
            try {
                if (skipExisting && repository.isAlreadyFetched(key)) {
                    skipped++;
                } else {
                    String content = fetcher.downloadLogFile(bucket, key);
                    var entries = parser.parse(content);
                    repository.saveEntries(key, entries);
                    if (entries.isEmpty()) log.warn("[-] {} — empty", key);
                    else log.info("[+] {} — {} entries", key, entries.size());
                    fetched++;
                }
            } catch (Exception e) {
                log.error("Failed to process {}: {}", key, e.getMessage());
                failed++;
            }
            progress.set(new FetchProgress(total, fetched + skipped + failed,
                    fetched, skipped, failed, false, null));
        }

        var result = new FetchResult(fetched, skipped, failed);
        progress.set(new FetchProgress(total, total,
                result.fetched(), result.skipped(), result.failed(), true, null));
        log.info("Done. Fetched: {}, Skipped: {}, Failed: {}",
                result.fetched(), result.skipped(), result.failed());
        return result;
    }

    @Async
    public void startAsync(LocalDate since, boolean skipExisting) {
        try {
            fetch(since, skipExisting);
        } catch (Exception e) {
            log.error("Async fetch failed: {}", e.getMessage(), e);
            FetchProgress prev = progress.get();
            progress.set(new FetchProgress(prev.total(), prev.processed(),
                    prev.fetched(), prev.skipped(), prev.failed(), true, e.getMessage()));
        }
    }
}
