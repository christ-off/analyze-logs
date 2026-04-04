package com.example.analyzelog.model;

import java.time.ZonedDateTime;

/**
 * Represents a single Amazon S3 Server Access Log entry.
 * See: https://docs.aws.amazon.com/AmazonS3/latest/userguide/LogFormat.html
 */
public record AccessLogEntry(
    String bucketOwner,
    String bucket,
    ZonedDateTime time,
    String remoteIp,
    String requester,
    String requestId,
    String operation,
    String key,
    String requestUri,
    int httpStatus,
    String errorCode,
    long bytesSent,
    long objectSize,
    long totalTime,
    long turnAroundTime,
    String referrer,
    String userAgent,
    String versionId
) {}
