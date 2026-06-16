package com.example.analyzelog.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class IpInfoService {

    public record IpInfo(String ip, String hostname, String org, String city, String country) {}

    private final RestClient restClient;
    private final ConcurrentHashMap<String, IpInfo> cache = new ConcurrentHashMap<>();

    public IpInfoService(RestClient restClient) {
        this.restClient = restClient;
    }

    public IpInfo lookup(String ip) {
        return cache.computeIfAbsent(ip, this::fetch);
    }

    private IpInfo fetch(String ip) {
        try {
            var response = restClient.get()
                    .uri("https://ipinfo.io/{ip}/json", ip)
                    .retrieve()
                    .body(IpInfoResponse.class);
            if (response == null) return fallback(ip);
            return new IpInfo(ip,
                    nvl(response.hostname()),
                    nvl(response.org()),
                    nvl(response.city()),
                    nvl(response.country()));
        } catch (Exception _) {
            return fallback(ip);
        }
    }

    private static IpInfo fallback(String ip) {
        return new IpInfo(ip, "?", "?", "?", "?");
    }

    private static String nvl(String s) {
        return (s == null || s.isBlank()) ? "?" : s;
    }

    record IpInfoResponse(String hostname, String org, String city, String country) {}
}
