package com.example.analyzelog.service;

import com.example.analyzelog.config.UaClassifierProperties;

import java.util.List;
import java.util.regex.Pattern;

public class UserAgentClassifier {

    private record Rule(Pattern pattern, String label) {}

    private final List<Rule> rules;

    public UserAgentClassifier(UaClassifierProperties properties) {
        this.rules = properties.rules().stream()
                .map(r -> new Rule(
                        Pattern.compile(Pattern.quote(r.pattern()), Pattern.CASE_INSENSITIVE),
                        r.label()))
                .toList();
    }

    public String classify(String ua) {
        if (ua == null || ua.isBlank()) return "(no user agent)";

        for (Rule r : rules) {
            if (r.pattern().matcher(ua).find()) return r.label();
        }

        boolean isEdge    = ua.contains("Edg/");
        boolean isChrome  = ua.contains("Chrome/") && !isEdge;
        boolean isFirefox = ua.contains("Firefox/");
        boolean isSafari  = ua.contains("Safari/") && ua.contains("Version/") && !isChrome && !isEdge;

        String os = detectOs(ua);

        if (isEdge)    return "Edge / " + os;
        if (isChrome)  return "Chrome / " + os;
        if (isFirefox) return "Firefox / " + os;
        if (isSafari)  return "Safari / " + os;

        return "Unknown";
    }

    private static String detectOs(String ua) {
        if (ua.contains("iPhone") || ua.contains("iPod")) return "iPhone";
        if (ua.contains("iPad"))                           return "iPad";
        if (ua.contains("Android"))                        return "Android";
        if (ua.contains("Windows NT"))                     return "Windows";
        if (ua.contains("Macintosh"))                      return "macOS";
        if (ua.contains("Linux"))                          return "Linux";
        return "Unknown OS";
    }
}