package com.example.analyzelog.model;

import java.time.Instant;

public record BotUaRequest(
    Instant timestamp,
    String clientIp,
    String uriStem,
    String resultType
) {}
