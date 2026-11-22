package com.example.analyzelog.model;

import java.time.Instant;

public record BotUaRequest(
    Instant timestamp,
    String clientIp,
    String asn,
    String asnName,
    String asnCategory,
    String uriStem,
    String referer,
    String resultType,
    String country,
    int scStatus
) {}
