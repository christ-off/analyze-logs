package com.example.analyzelog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "uri-stem-groups")
public record UriStemGroupProperties(List<Group> groups) {
    public record Group(String name, List<String> patterns) {}
}