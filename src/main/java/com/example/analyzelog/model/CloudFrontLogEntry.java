package com.example.analyzelog.model;

import java.time.Instant;

public record CloudFrontLogEntry(
    Instant timestamp,
    String edgeLocation,
    String clientIp,
    String method,
    String uriStem,
    int status,
    String referer,
    String userAgent,
    String edgeResultType,
    String edgeResponseResultType,
    String edgeDetailedResultType,
    String contentType,
    String country
) {}
