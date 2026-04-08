package com.example.analyzelog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "ua-groups")
public record UaGroupProperties(List<Group> groups) {

    public record Group(String name, List<String> labels, List<String> labelPrefixes) {}
}
