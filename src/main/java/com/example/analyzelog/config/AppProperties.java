package com.example.analyzelog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(AwsProperties aws, String dbPath, int topLimit, int topUrlsLimit, String robotsUrl) {

    public record AwsProperties(String region, String bucket, String prefix) {}
}