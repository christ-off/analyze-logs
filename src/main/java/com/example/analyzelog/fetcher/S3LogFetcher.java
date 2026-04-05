package com.example.analyzelog.fetcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

@Component
public class S3LogFetcher {

    private static final Logger log = LoggerFactory.getLogger(S3LogFetcher.class);

    private final S3Client s3;

    public S3LogFetcher(S3Client s3) {
        this.s3 = s3;
    }

    public String downloadLogFile(String bucket, String key) {
        log.debug("Downloading s3://{}/{}", bucket, key);
        ResponseBytes<GetObjectResponse> response = s3.getObjectAsBytes(
            GetObjectRequest.builder().bucket(bucket).key(key).build()
        );
        byte[] bytes = response.asByteArray();
        if (key.endsWith(".gz")) {
            try (var gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to decompress " + key, e);
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public void streamLogKeys(String bucket, String prefix, Instant since, Consumer<String> keyConsumer) {
        var request = ListObjectsV2Request.builder()
            .bucket(bucket)
            .prefix(prefix)
            .build();

        ListObjectsV2Iterable pages = s3.listObjectsV2Paginator(request);
        log.info("Scanning s3://{}/{}", bucket, prefix);

        pages.stream()
            .flatMap(page -> page.contents().stream())
            .filter(obj -> since == null || obj.lastModified().isAfter(since))
            .map(S3Object::key)
            .forEach(keyConsumer);
    }
}