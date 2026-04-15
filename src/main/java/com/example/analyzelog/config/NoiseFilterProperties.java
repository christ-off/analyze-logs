package com.example.analyzelog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "noise-filter")
public record NoiseFilterProperties(List<Rule> rules) {
    public record Rule(String uaName, String uriStem) {}
}
