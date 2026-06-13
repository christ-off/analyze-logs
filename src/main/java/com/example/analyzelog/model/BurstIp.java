package com.example.analyzelog.model;

public record BurstIp(String clientIp, long maxPerMinute, long total, String country) {}
