package com.example.analyzelog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "uri-stem-filter")
public record UriStemFilterProperties(List<String> excludedExtensions) {}
