package com.example.analyzelog.service;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.fetcher.S3LogFetcher;
import com.example.analyzelog.model.CloudFrontLogEntry;
import com.example.analyzelog.parser.CloudFrontLogParser;
import com.example.analyzelog.repository.LogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FetchServiceTest {

    @Mock S3LogFetcher fetcher;
    @Mock CloudFrontLogParser parser;
    @Mock LogRepository repository;
    @Mock AppProperties props;
    @Mock AppProperties.AwsProperties awsProps;

    @InjectMocks FetchService fetchService;

    @BeforeEach
    void setUp() {
        when(props.aws()).thenReturn(awsProps);
        when(awsProps.bucket()).thenReturn("my-bucket");
        when(awsProps.prefix()).thenReturn("logs/");
    }

    private void stubFetcher(String... keys) {
        doAnswer(inv -> {
            Consumer<String> consumer = inv.getArgument(3);
            for (String key : keys) consumer.accept(key);
            return null;
        }).when(fetcher).streamLogKeys(any(), any(), any(), any());
    }

    @Test
    void skipsAlreadyFetchedKey() {
        stubFetcher("key1.log");
        when(repository.isAlreadyFetched("key1.log")).thenReturn(true);

        var result = fetchService.fetch(LocalDate.now(), true);

        assertEquals(0, result.fetched());
        assertEquals(1, result.skipped());
        assertEquals(0, result.failed());
        verify(fetcher, never()).downloadLogFile(any(), any());
    }

    @Test
    void fetchesNewKey() {
        stubFetcher("key1.log");
        when(repository.isAlreadyFetched("key1.log")).thenReturn(false);
        when(fetcher.downloadLogFile("my-bucket", "key1.log")).thenReturn("content");
        when(parser.parse("content")).thenReturn(List.of(mock(CloudFrontLogEntry.class)));

        var result = fetchService.fetch(LocalDate.now(), true);

        assertEquals(1, result.fetched());
        assertEquals(0, result.skipped());
        assertEquals(0, result.failed());
        verify(repository).saveEntries(eq("key1.log"), anyList());
    }

    @Test
    void logsWarningForEmptyEntries() {
        stubFetcher("empty.log");
        when(repository.isAlreadyFetched("empty.log")).thenReturn(false);
        when(fetcher.downloadLogFile("my-bucket", "empty.log")).thenReturn("");
        when(parser.parse("")).thenReturn(List.of());

        var result = fetchService.fetch(null, true);

        assertEquals(1, result.fetched());
        assertEquals(0, result.failed());
    }

    @Test
    void countsFailedOnException() {
        stubFetcher("bad.log");
        when(fetcher.downloadLogFile("my-bucket", "bad.log"))
                .thenThrow(new RuntimeException("S3 error"));

        var result = fetchService.fetch(null, false);

        assertEquals(0, result.fetched());
        assertEquals(1, result.failed());
    }

    @Test
    void sinceNullPassedAsInstantNull() {
        stubFetcher();
        fetchService.fetch(null, false);

        verify(fetcher).streamLogKeys(eq("my-bucket"), eq("logs/"), isNull(), any());
    }

    @Test
    void sinceConvertedToInstant() {
        stubFetcher();
        fetchService.fetch(LocalDate.of(2026, 1, 1), false);

        verify(fetcher).streamLogKeys(eq("my-bucket"), eq("logs/"),
                eq(Instant.parse("2026-01-01T00:00:00Z")), any());
    }

    @Test
    void skipExistingFalseDoesNotCheckRepository() {
        stubFetcher("key1.log");
        when(fetcher.downloadLogFile("my-bucket", "key1.log")).thenReturn("data");
        when(parser.parse("data")).thenReturn(List.of(mock(CloudFrontLogEntry.class)));

        fetchService.fetch(null, false);

        verify(repository, never()).isAlreadyFetched(any());
        verify(repository).saveEntries(eq("key1.log"), anyList());
    }
}