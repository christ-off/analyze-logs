package com.example.analyzelog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties(prefix = "uri-stem-groups")
public record UriStemGroupProperties(List<Group> groups) {
    public record Group(String name, List<String> patterns, @DefaultValue("false") boolean security) {}
}