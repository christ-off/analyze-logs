package com.example.analyzelog.model;

import java.time.Instant;

public record IpRequest(
    Instant timestamp,
    String clientIp,
    String name,
    String country,
    String userAgent,
    String uriStem,
    String resultType,
    int scStatus
) {}
