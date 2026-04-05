package com.example.analyzelog.model;

import java.time.Instant;

public record CloudFrontLogEntry(
    Instant timestamp,            // date + time (UTC)
    String edgeLocation,          // x-edge-location
    long scBytes,                 // sc-bytes
    String clientIp,              // c-ip
    String method,                // cs-method
    String uriStem,               // cs-uri-stem
    int status,                   // sc-status
    String referer,               // cs(Referer)
    String userAgent,             // cs(User-Agent)
    String edgeResultType,        // x-edge-result-type
    String protocol,              // cs-protocol
    long csBytes,                 // cs-bytes
    double timeTaken,             // time-taken
    String edgeResponseResultType,// x-edge-response-result-type
    String protocolVersion,       // cs-protocol-version
    double timeToFirstByte,       // time-to-first-byte
    String edgeDetailedResultType,// x-edge-detailed-result-type
    String contentType,           // sc-content-type
    Long contentLength,           // sc-content-len
    String country                // c-country
) {}