package com.example.analyzelog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "ua-classifier")
public record UaClassifierProperties(List<Rule> rules) {

    public record Rule(String pattern, String label) {}
}