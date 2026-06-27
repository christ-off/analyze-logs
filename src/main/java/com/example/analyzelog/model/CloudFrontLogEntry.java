package com.example.analyzelog.model;

import java.time.Instant;

public record CloudFrontLogEntry(
    Instant timestamp,
    String edgeLocation,
    long scBytes,
    String clientIp,
    String method,
    String uriStem,
    int status,
    String referer,
    String userAgent,
    String edgeResultType,
    long csBytes,
    double timeTaken,
    String edgeResponseResultType,
    double timeToFirstByte,
    String edgeDetailedResultType,
    String contentType,
    Long contentLength,
    String country
) {}
