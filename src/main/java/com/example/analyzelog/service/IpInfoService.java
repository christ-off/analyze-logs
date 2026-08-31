package com.example.analyzelog.service;

import com.fasterxml.jackson.annotation.JsonProperty;
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
        IpInfo result = fetchFromIpInfo(ip);
        if (result != null) return result;
        result = fetchFromIpWho(ip);
        if (result != null) return result;
        result = fetchFromIpApiCo(ip);
        if (result != null) return result;
        return fallback(ip);
    }

    private IpInfo fetchFromIpInfo(String ip) {
        try {
            var response = restClient.get()
                    .uri("https://ipinfo.io/{ip}/json", ip)
                    .retrieve()
                    .body(IpInfoResponse.class);
            if (response == null) return null;
            return new IpInfo(ip,
                    nvl(response.hostname()),
                    nvl(response.org()),
                    nvl(response.city()),
                    nvl(response.country()));
        } catch (Exception _) {
            return null;
        }
    }

    private IpInfo fetchFromIpWho(String ip) {
        try {
            var response = restClient.get()
                    .uri("https://ipwho.is/{ip}", ip)
                    .retrieve()
                    .body(IpWhoResponse.class);
            if (response == null || !response.success() || response.connection() == null) return null;
            return new IpInfo(ip,
                    "?",
                    nvl(response.connection().org()),
                    nvl(response.city()),
                    nvl(response.country()));
        } catch (Exception _) {
            return null;
        }
    }

    private IpInfo fetchFromIpApiCo(String ip) {
        try {
            var response = restClient.get()
                    .uri("https://ipapi.co/{ip}/json/", ip)
                    .retrieve()
                    .body(IpApiCoResponse.class);
            if (response == null || Boolean.TRUE.equals(response.error())) return null;
            return new IpInfo(ip,
                    "?",
                    nvl(response.org()),
                    nvl(response.city()),
                    nvl(response.countryName()));
        } catch (Exception _) {
            return null;
        }
    }

    private static IpInfo fallback(String ip) {
        return new IpInfo(ip, "?", "?", "?", "?");
    }

    private static String nvl(String s) {
        return (s == null || s.isBlank()) ? "?" : s;
    }

    record IpInfoResponse(String hostname, String org, String city, String country) {}

    record IpWhoResponse(boolean success, String city, String country, Connection connection) {
        record Connection(String org) {}
    }

    record IpApiCoResponse(String org, String city,
                            @JsonProperty("country_name") String countryName,
                            Boolean error) {}
}
