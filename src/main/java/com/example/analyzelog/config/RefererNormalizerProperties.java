package com.example.analyzelog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "referer-normalizer")
public record RefererNormalizerProperties(List<Rule> rules) {

    public record Rule(String label, String domain, String domainStartsWith, String domainEndsWith) {}
}