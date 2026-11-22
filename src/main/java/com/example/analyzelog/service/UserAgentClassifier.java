package com.example.analyzelog.service;

import java.util.List;
import java.util.regex.Pattern;

public class UserAgentClassifier {

    // Both the detectOs() label and the token raw iOS UA strings carry.
    private static final String IPHONE = "iPhone";

    private record CompiledRule(Pattern pattern, String label) {}

    private final List<CompiledRule> rules;

    public UserAgentClassifier(List<UaClassifierRule> rules) {
        this.rules = rules.stream()
                .map(r -> new CompiledRule(
                        Pattern.compile(Pattern.quote(r.pattern()), Pattern.CASE_INSENSITIVE),
                        r.label()))
                .toList();
    }

    public String classify(String ua) {
        if (ua == null || ua.isBlank()) return "(no user agent)";

        for (CompiledRule r : rules) {
            if (r.pattern().matcher(ua).find()) return r.label();
        }

        String os = detectOs(ua);
        String browser = detectBrowser(ua, os);
        return browser == null ? "Unknown" : browser + " / " + os;
    }

    // Browser family for raw UA strings no static rule matched, or null for unrecognised ones.
    private static String detectBrowser(String ua, String os) {
        boolean isEdge    = ua.contains("Edg/");
        boolean isChrome  = (ua.contains("Chrome/") || ua.contains("CriOS/")) && !isEdge;
        boolean isFirefox = ua.contains("Firefox/") || ua.contains("FxiOS/");
        boolean isSafari  = ua.contains("Safari/") && ua.contains("Version/") && !isChrome && !isEdge;

        if (isEdge)    return "Edge";
        if (isChrome)  return "Chrome";
        if (isFirefox) return "Firefox";
        if (isSafari)  return "Safari";

        // Embedded WebKit view (WKWebView): AppleWebKit without the "Version/x Safari/x" tokens
        if (ua.contains("AppleWebKit/") && !ua.contains("Safari/")
                && (os.equals("macOS") || os.equals(IPHONE) || os.equals("iPad"))) {
            return "WebView";
        }

        return null;
    }

    private static String detectOs(String ua) {
        if (ua.contains(IPHONE) || ua.contains("iPod")) return IPHONE;
        if (ua.contains("iPad"))                           return "iPad";
        if (ua.contains("Android"))                        return "Android";
        if (ua.contains("Windows NT"))                     return "Windows";
        if (ua.contains("Macintosh"))                      return "macOS";
        if (ua.contains("Linux"))                          return "Linux";
        return "Unknown OS";
    }
}
