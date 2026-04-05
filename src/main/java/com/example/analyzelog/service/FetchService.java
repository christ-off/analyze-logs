package com.example.analyzelog.service;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.fetcher.S3LogFetcher;
import com.example.analyzelog.parser.CloudFrontLogParser;
import com.example.analyzelog.repository.LogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class FetchService {

    private static final Logger log = LoggerFactory.getLogger(FetchService.class);

    private final S3LogFetcher fetcher;
    private final CloudFrontLogParser parser;
    private final LogRepository repository;
    private final AppProperties props;

    public FetchService(S3LogFetcher fetcher, CloudFrontLogParser parser,
                        LogRepository repository, AppProperties props) {
        this.fetcher = fetcher;
        this.parser = parser;
        this.repository = repository;
        this.props = props;
    }

    public record FetchResult(int fetched, int skipped, int failed) {}

    public FetchResult fetch(LocalDate since, boolean skipExisting) {
        String bucket = props.aws().bucket();
        String prefix = props.aws().prefix();
        Instant sinceInstant = since != null ? since.atStartOfDay(ZoneOffset.UTC).toInstant() : null;

        var fetched = new AtomicInteger();
        var skipped = new AtomicInteger();
        var failed = new AtomicInteger();

        fetcher.streamLogKeys(bucket, prefix, sinceInstant, key -> {
            try {
                if (skipExisting && repository.isAlreadyFetched(key)) {
                    skipped.incrementAndGet();
                    return;
                }

                String content = fetcher.downloadLogFile(bucket, key);
                var entries = parser.parse(content);
                repository.saveEntries(key, entries);

                if (entries.isEmpty()) {
                    log.warn("[-] {} — empty", key);
                } else {
                    log.info("[+] {} — {} entries", key, entries.size());
                }
                fetched.incrementAndGet();

            } catch (Exception e) {
                log.error("Failed to process {}: {}", key, e.getMessage());
                failed.incrementAndGet();
            }
        });

        log.info("Done. Fetched: {}, Skipped: {}, Failed: {}",
            fetched.get(), skipped.get(), failed.get());

        return new FetchResult(fetched.get(), skipped.get(), failed.get());
    }
}