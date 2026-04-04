package com.example.analyzelog.fetcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class S3LogFetcher implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(S3LogFetcher.class);

    private final S3Client s3;

    public S3LogFetcher(String region, String profile) {
        var credentialsProvider = profile != null
            ? ProfileCredentialsProvider.create(profile)
            : DefaultCredentialsProvider.create();

        this.s3 = S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(credentialsProvider)
            .build();
    }

    /**
     * Lists all log object keys in the given bucket/prefix, optionally filtered by modification time.
     */
    public List<String> listLogKeys(String bucket, String prefix, Instant since) {
        var request = ListObjectsV2Request.builder()
            .bucket(bucket)
            .prefix(prefix)
            .build();

        ListObjectsV2Iterable pages = s3.listObjectsV2Paginator(request);

        var keys = new ArrayList<String>();
        for (var page : pages) {
            for (S3Object obj : page.contents()) {
                if (since == null || obj.lastModified().isAfter(since)) {
                    keys.add(obj.key());
                }
            }
        }

        log.info("Found {} log files in s3://{}/{}", keys.size(), bucket, prefix);
        return keys;
    }

    /**
     * Downloads a single log file and returns its content as a string.
     */
    public String downloadLogFile(String bucket, String key) {
        log.debug("Downloading s3://{}/{}", bucket, key);
        ResponseBytes<GetObjectResponse> response = s3.getObjectAsBytes(
            GetObjectRequest.builder().bucket(bucket).key(key).build()
        );
        return response.asString(StandardCharsets.UTF_8);
    }

    /**
     * Streams log file keys one by one, invoking the consumer for each.
     * Useful for large buckets to avoid loading all keys into memory.
     */
    public void streamLogKeys(String bucket, String prefix, Instant since, Consumer<String> keyConsumer) {
        var request = ListObjectsV2Request.builder()
            .bucket(bucket)
            .prefix(prefix)
            .build();

        s3.listObjectsV2Paginator(request).stream()
            .flatMap(page -> page.contents().stream())
            .filter(obj -> since == null || obj.lastModified().isAfter(since))
            .map(S3Object::key)
            .forEach(keyConsumer);
    }

    @Override
    public void close() {
        s3.close();
    }
}
