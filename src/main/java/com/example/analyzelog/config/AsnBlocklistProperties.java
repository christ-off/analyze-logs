package com.example.analyzelog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

// allowed: hosting ASNs never blocked; forced: ASNs blocked whatever their category.
@ConfigurationProperties(prefix = "asn-blocklist")
public record AsnBlocklistProperties(List<String> allowed, List<String> forced) {}
