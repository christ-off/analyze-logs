package com.example.analyzelog.fetcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3LogFetcherTest {

    @Mock S3Client s3;
    @InjectMocks S3LogFetcher fetcher;

    private void stubS3Download(String key, byte[] bytes) {
        @SuppressWarnings("unchecked")
        ResponseBytes<GetObjectResponse> response = mock(ResponseBytes.class);
        when(response.asByteArray()).thenReturn(bytes);
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(response);
    }

    private static byte[] gzip(String text) {
        try (var baos = new ByteArrayOutputStream();
             var gos  = new GZIPOutputStream(baos)) {
            gos.write(text.getBytes(StandardCharsets.UTF_8));
            gos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void downloadPlainTextFile() {
        String content = "log line one\nlog line two\n";
        stubS3Download("file.log", content.getBytes(StandardCharsets.UTF_8));

        String result = fetcher.downloadLogFile("bucket", "file.log");

        assertEquals(content, result);
    }

    @Test
    void downloadGzipFile() {
        String content = "compressed log content\n";
        stubS3Download("file.log.gz", gzip(content));

        String result = fetcher.downloadLogFile("bucket", "file.log.gz");

        assertEquals(content, result);
    }

    @Test
    void streamLogKeysWithoutSinceReturnsAll() {
        var obj1 = S3Object.builder().key("a.log").lastModified(Instant.EPOCH).build();
        var obj2 = S3Object.builder().key("b.log").lastModified(Instant.now()).build();
        var page  = ListObjectsV2Response.builder().contents(obj1, obj2).build();
        var iterable = mock(ListObjectsV2Iterable.class);
        when(iterable.stream()).thenReturn(Stream.of(page));
        when(s3.listObjectsV2Paginator(any(ListObjectsV2Request.class))).thenReturn(iterable);

        List<String> collected = new ArrayList<>();
        fetcher.streamLogKeys("bucket", "prefix/", null, collected::add);

        assertEquals(List.of("a.log", "b.log"), collected);
    }

    @Test
    void streamLogKeysFiltersByLastModified() {
        Instant cutoff = Instant.parse("2026-01-01T00:00:00Z");
        var old = S3Object.builder().key("old.log").lastModified(Instant.EPOCH).build();
        var recent = S3Object.builder().key("new.log").lastModified(Instant.parse("2026-06-01T00:00:00Z")).build();
        var page = ListObjectsV2Response.builder().contents(old, recent).build();
        var iterable = mock(ListObjectsV2Iterable.class);
        when(iterable.stream()).thenReturn(Stream.of(page));
        when(s3.listObjectsV2Paginator(any(ListObjectsV2Request.class))).thenReturn(iterable);

        List<String> collected = new ArrayList<>();
        fetcher.streamLogKeys("bucket", "prefix/", cutoff, collected::add);

        assertEquals(List.of("new.log"), collected);
    }
}