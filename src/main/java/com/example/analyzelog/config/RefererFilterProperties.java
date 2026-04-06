package com.example.analyzelog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "referer-filter")
public record RefererFilterProperties(List<String> selfReferers) {}