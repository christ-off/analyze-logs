package com.example.analyzelog.service;

import com.example.analyzelog.config.UaClassifierProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserAgentClassifierTest {

    private static UserAgentClassifier classifier;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void loadClassifier() throws Exception {
        var yaml = new Yaml();
        try (var is = UserAgentClassifierTest.class.getResourceAsStream("/application.yml")) {
            Map<String, Object> root = yaml.load(is);
            var uaSection = (Map<String, Object>) root.get("ua-classifier");
            var rawRules = (List<Map<String, String>>) uaSection.get("rules");
            var rules = rawRules.stream()
                    .map(r -> new UaClassifierProperties.Rule(r.get("pattern"), r.get("label")))
                    .toList();
            classifier = new UserAgentClassifier(new UaClassifierProperties(rules));
        }
    }

    @ParameterizedTest
    @CsvSource({
        // AI crawlers
        "'Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko; compatible; ClaudeBot/1.0; +claudebot@anthropic.com)', ClaudeBot",
        "'Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko; compatible; Claude-SearchBot/1.0)', Claude-SearchBot",
        "'Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko; compatible; OAI-SearchBot/1.3)', OAI-SearchBot",
        "'Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko; compatible; PerplexityBot/1.0)', PerplexityBot",
        // SEO crawlers
        "'Mozilla/5.0 (compatible; AhrefsBot/7.0; +http://ahrefs.com/robot/)', AhrefsBot",
        "'Mozilla/5.0 (compatible; SemrushBot/7~bl; +http://www.semrush.com/bot.html)', SemrushBot",
        "'Mozilla/5.0 (compatible; SERankingBacklinksBot/1.0)', SERankingBot",
        "'Mozilla/5.0 (compatible; Barkrowler/0.9)', Barkrowler",
        "'Mozilla/5.0 (compatible; DotBot/1.2)', DotBot",
        "'Mozilla/5.0 (compatible; MJ12bot/v2.0.5)', MJ12bot",
        // Search bots
        "'Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)', Googlebot",
        "'Mozilla/5.0 (Windows NT 5.1; rv:11.0) Gecko Firefox/11.0 (via ggpht.com GoogleImageProxy)', Google ImageProxy",
        "'FeedFetcher-Google; (+http://www.google.com/feedfetcher.html)', Google FeedFetcher",
        "'Mozilla/5.0 AppleWebKit/537.36 (compatible; bingbot/2.0)', Bingbot",
        "'Mozilla/5.0 (compatible; YandexBot/3.0)', YandexBot",
        "'Mozilla/5.0 (compatible; Baiduspider/2.0)', Baiduspider",
        "'DuckDuckBot/1.1', DuckDuckBot",
        "'Mozilla/5.0 (compatible; Qwantbot/1.0)', Qwantbot",
        // Security scanners
        "'Mozilla/5.0 (compatible; WellKnownBot/0.1)', WellKnownBot",
        "'Mozilla/5.0 (compatible; CensysInspect/1.1)', Censys Scanner",
        "'Hello from Palo Alto Networks, find out more about our scans', Palo Alto Scanner",
        "'visionheight.com/scan Mozilla/5.0', VisionHeight Scanner",
        "'2ip bot/1.1 (+https://2ip.io)', 2ip Scanner",
        // Feed readers
        "'Feedly/1.0 (+http://www.feedly.com/fetcher.html)', Feedly",
        "'Feedbin feed-id:2878303 - 1 subscribers', Feedbin",
        "'Reeder/5050102 CFNetwork/3860.500.112 Darwin/25.4.0', Reeder",
        "'FreshRSS/1.28.1 (Linux; https://freshrss.org)', FreshRSS",
        "'NewsBlur Feed Fetcher - 1 subscriber', NewsBlur",
        "'rss-parser', rss-parser",
        "'sfFeedReader/0.9', sfFeedReader",
        "'Mozilla/5.0 (feeder.co; Macintosh) AppleWebKit/537.36', Feeder",
        "'flus/2.3.1 (https://app.flus.fr/about)', Flus",
        "'wp.com feedbot/1.0', WP.com FeedBot",
        // Fediverse
        "'http.rb/5.1.1 (Mastodon/4.2.17; +https://mastodon.example.org/)', Mastodon",
        "'Misskey/2025.4.6 (https://example.com)', Misskey",
        "'SummalyBot/5.2.5', SummalyBot",
        "'Yumechi-no-Kuni-Proxy-Worker (+https://forge.yumechi.jp)', Yumechi Proxy",
        // Social
        "'facebookexternalhit/1.1', Facebook",
        // Apps
        "'Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 obsidian/1.6.5 Chrome/124.0 Electron/30 Safari/537.36', Obsidian",
        "'Mozilla/5.0 (iPhone) AppleWebKit/605.1.15 GSA/414.0 Mobile/15E148 Safari/604.1', Google Search App",
        "'okhttp/5.3.0', okhttp",
        "'Go-http-client/1.1', Go HTTP client",
        // Headless — must win over generic Chrome rule
        "'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 HeadlessChrome/138.0 Safari/537.36', HeadlessChrome",
        // Real browsers
        "'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36', 'Chrome / Windows'",
        "'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/139.0.0.0 Safari/537.36', 'Chrome / macOS'",
        "'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/145.0.0.0 Safari/537.36', 'Chrome / Linux'",
        "'Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/146.0.0.0 Mobile Safari/537.36', 'Chrome / Android'",
        "'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/146.0.0.0 Safari/537.36 Edg/146.0.0.0', 'Edge / Windows'",
        "'Mozilla/5.0 (X11; Linux x86_64; rv:147.0) Gecko/20100101 Firefox/147.0', 'Firefox / Linux'",
        "'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:149.0) Gecko/20100101 Firefox/149.0', 'Firefox / Windows'",
        "'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.4 Safari/605.1.15', 'Safari / macOS'",
        "'Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) AppleWebKit/605.1.15 Version/26.4 Mobile/15E148 Safari/604.1', 'Safari / iPhone'",
        // No user agent
        "'', '(no user agent)'",
        "'   ', '(no user agent)'"
    })
    void classifiesCorrectly(String ua, String expected) {
        assertEquals(expected, classifier.classify(ua));
    }

    @ParameterizedTest
    @NullSource
    void classifiesNull(String ua) {
        assertEquals("(no user agent)", classifier.classify(ua));
    }
}