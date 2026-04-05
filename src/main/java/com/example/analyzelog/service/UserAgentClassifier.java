package com.example.analyzelog.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class UserAgentClassifier {

    private static final Logger log = LoggerFactory.getLogger(UserAgentClassifier.class);

    private record Rule(Pattern pattern, String label) {}

    private static final List<Rule> RULES = loadRules();

    public static String classify(String ua) {
        if (ua == null || ua.isBlank()) return "(no user agent)";

        for (Rule r : RULES) {
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

    @SuppressWarnings("unchecked")
    private static List<Rule> loadRules() {
        try (InputStream is = openConfig()) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(is);
            Map<String, Object> section = (Map<String, Object>) root.get("ua-classifier");
            List<Map<String, String>> rawRules = (List<Map<String, String>>) section.get("rules");
            return rawRules.stream()
                .map(r -> new Rule(
                    Pattern.compile(Pattern.quote(r.get("pattern")), Pattern.CASE_INSENSITIVE),
                    r.get("label")))
                .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load ua-classifier rules from application.yml", e);
        }
    }

    private static InputStream openConfig() throws Exception {
        Path external = Path.of("application.yml");
        if (Files.exists(external)) {
            log.debug("Loading ua-classifier rules from external {}", external.toAbsolutePath());
            return Files.newInputStream(external);
        }
        log.debug("Loading ua-classifier rules from classpath application.yml");
        InputStream classpath = UserAgentClassifier.class.getClassLoader()
            .getResourceAsStream("application.yml");
        if (classpath == null) {
            throw new IllegalStateException("application.yml not found on classpath");
        }
        return classpath;
    }
}