package com.example.analyzelog.service;

import com.example.analyzelog.model.CloudFrontLogEntry;
import com.example.analyzelog.model.DailyResultTypeCount;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.model.NameResultTypeCount;
import com.example.analyzelog.repository.LogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DashboardServiceIntegrationTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("dashboard-test.db");
        registry.add("spring.datasource.url", () -> dbUrl);
        registry.add("app.db-path", () -> tempDir.resolve("dashboard-test.db").toString());
        registry.add("referer-filter.self-referers[0]", () -> "https://post-tenebras-lire.net/");
        registry.add("referer-filter.self-referers[1]", () -> "http://post-tenebras-lire.net/");
    }

    @Autowired
    LogRepository repository;

    @Autowired
    DashboardService dashboardService;

    @Test
    void countryTopUserAgentsByResultType_filtersToCountryAndCountsPerResultType() {
        Instant from = Instant.now();
        repository.saveEntries("logs/country-ua-split-test.gz", List.of(
                entryWithUaAndCountryAndResultType(UA_CHROME_WINDOWS, "FR", "Hit"),
                entryWithUaAndCountryAndResultType(UA_CHROME_WINDOWS, "FR", "Hit"),
                entryWithUaAndCountryAndResultType(UA_CHROME_WINDOWS, "FR", "Miss"),
                entryWithUaAndCountryAndResultType(UA_FIREFOX_LINUX,  "FR", "Error"),
                entryWithUaAndCountryAndResultType(UA_CHROME_WINDOWS, "US", "Hit")  // different country — must not appear
        ));

        var result = dashboardService.countryTopUserAgentsByResultType("FR", from, Instant.now().plusSeconds(5), 10, false);

        assertFalse(result.isEmpty());
        var chrome = result.stream().filter(r -> "Chrome / Windows".equals(r.name())).findFirst().orElseThrow();
        assertEquals(2, chrome.hit());
        assertEquals(1, chrome.miss());
        assertEquals(0, chrome.error());

        var firefox = result.stream().filter(r -> "Firefox / Linux".equals(r.name())).findFirst().orElseThrow();
        assertEquals(1, firefox.error());

        // US entry must not inflate FR counts
        long totalHits = result.stream().mapToLong(NameResultTypeCount::hit).sum();
        assertEquals(2, totalHits);
    }

    @Test
    void topUrlsByResultType_excludesStaticExtensions() {
        Instant from = Instant.now();
        repository.saveEntries("logs/urls-split-filter-test.gz", List.of(
                entryWithUri("/index.html"),
                entryWithUri("/about.html"),
                entryWithUri("/about.html"),
                entryWithUri("/style.css"),
                entryWithUri("/app.js"),
                entryWithUri("/logo.png"),
                entryWithUri("/icon.svg")
        ));

        var result = dashboardService.topUrlsByResultType(from, Instant.now().plusSeconds(5), 10, false);

        var names = result.stream().map(r -> r.name()).toList();
        assertTrue(names.contains("/about.html"));
        assertTrue(names.contains("/index.html"));
        assertFalse(names.contains("/style.css"));
        assertFalse(names.contains("/app.js"));
        assertFalse(names.contains("/logo.png"));
        assertFalse(names.contains("/icon.svg"));
    }

    @Test
    void topUrlsByResultType_countsResultTypesAndAggregatesPhpWordPress() {
        Instant from = Instant.now();
        repository.saveEntries("logs/urls-split-rt-test.gz", List.of(
                entryWithUriAndResultType("/index.html", "Hit"),
                entryWithUriAndResultType("/index.html", "Miss"),
                entryWithUriAndResultType("/page.php",   "Hit"),
                entryWithUriAndResultType("/other.php",  "Error"),
                entryWithUriAndResultType("/wp-login.php", "Miss"),
                entryWithUriAndResultType("/wp-content/themes/style", "Hit")
        ));

        var result = dashboardService.topUrlsByResultType(from, Instant.now().plusSeconds(5), 10, false);

        var names = result.stream().map(r -> r.name()).toList();
        assertTrue(names.contains("/index.html"));
        assertTrue(names.contains("PHP"));
        assertTrue(names.contains("WordPress"));
        assertFalse(names.contains("/page.php"));
        assertFalse(names.contains("/wp-login.php"));

        var index = result.stream().filter(r -> "/index.html".equals(r.name())).findFirst().orElseThrow();
        assertEquals(1, index.hit());
        assertEquals(1, index.miss());

        var php = result.stream().filter(r -> "PHP".equals(r.name())).findFirst().orElseThrow();
        assertEquals(1, php.hit());
        assertEquals(1, php.error());

        var wp = result.stream().filter(r -> "WordPress".equals(r.name())).findFirst().orElseThrow();
        assertEquals(1, wp.hit());
        assertEquals(1, wp.miss());
    }

    @Test
    void countryUrlsByResultType_countsPerResultTypeAndExcludesStaticExtensions() {
        Instant from = Instant.now();
        repository.saveEntries("logs/country-urls-split-test.gz", List.of(
                entryWithCountryAndUriAndResultType("FR", "/index.html", "Hit"),
                entryWithCountryAndUriAndResultType("FR", "/index.html", "Hit"),
                entryWithCountryAndUriAndResultType("FR", "/index.html", "Miss"),
                entryWithCountryAndUriAndResultType("FR", "/page.php",   "Error"),
                entryWithCountryAndUriAndResultType("FR", "/style.css",  "Hit"),  // excluded
                entryWithCountryAndUriAndResultType("US", "/index.html", "Hit")   // different country
        ));

        var result = dashboardService.countryUrlsByResultType("FR", from, Instant.now().plusSeconds(5), 10, false);

        var names = result.stream().map(r -> r.name()).toList();
        assertTrue(names.contains("/index.html"));
        assertTrue(names.contains("PHP"));
        assertFalse(names.contains("/style.css"), "static extensions must be excluded");

        var index = result.stream().filter(r -> "/index.html".equals(r.name())).findFirst().orElseThrow();
        assertEquals(2, index.hit());
        assertEquals(1, index.miss());

        // US entries must not appear
        long total = result.stream().mapToLong(r -> r.hit() + r.miss() + r.function() + r.error()).sum();
        assertEquals(4, total);
    }

    @Test
    void topCountriesByResultType_countsPerResultTypeAndResolvesDisplayName() {
        Instant from = Instant.now();
        repository.saveEntries("logs/countries-split-test.gz", List.of(
                entryWithCountryAndResultType("FR", "Hit"),
                entryWithCountryAndResultType("FR", "Hit"),
                entryWithCountryAndResultType("FR", "Error"),
                entryWithCountryAndResultType("US", "Miss")
        ));

        var result = dashboardService.topCountriesByResultType(from, Instant.now().plusSeconds(5), 10, false);

        assertFalse(result.isEmpty());
        var fr = result.stream().filter(r -> "FR".equals(r.code())).findFirst().orElseThrow();
        assertEquals("France", fr.name());
        assertEquals(2, fr.hit());
        assertEquals(1, fr.error());
        assertEquals(0, fr.miss());

        var us = result.stream().filter(r -> "US".equals(r.code())).findFirst().orElseThrow();
        assertEquals("United States", us.name());
        assertEquals(1, us.miss());
    }

    @Test
    void topEdgeLocations_resolvesToHumanReadable() {
        Instant from = Instant.now();
        repository.saveEntries("logs/edge-test.gz", List.of(
                entry("SFO53-P7"),
                entry("SFO53-P7"),
                entry("CDG55-P2")
        ));

        var result = dashboardService.topEdgeLocations(from, Instant.now().plusSeconds(5), 10);

        assertFalse(result.isEmpty());
        var top = result.getFirst();
        assertEquals("San Francisco, United States", top.name());
        assertEquals(2, top.count());
    }

    @Test
    void topEdgeLocations_secondEntryIsResolved() {
        Instant from = Instant.now();
        repository.saveEntries("logs/edge-test-2.gz", List.of(
                entry("CDG55-P2"),
                entry("LHR5-P1")
        ));

        var result = dashboardService.topEdgeLocations(from, Instant.now().plusSeconds(5), 10);

        var names = result.stream().map(NameCount::name).toList();
        assertTrue(names.stream().anyMatch(n -> n.contains("Paris")));
        assertTrue(names.stream().anyMatch(n -> n.contains("London")));
    }

    @Test
    void requestsPerDay_countsPerResultType() {
        Instant from = Instant.now();
        repository.saveEntries("logs/result-type-test.gz", List.of(
                entryWithResultType("Hit"),
                entryWithResultType("Hit"),
                entryWithResultType("Miss"),
                entryWithResultType("Error"),
                entryWithResultType("FunctionExecutionError")
        ));

        List<DailyResultTypeCount> result = dashboardService.requestsPerDay(
                from, Instant.now().plusSeconds(5), false);

        assertFalse(result.isEmpty());
        DailyResultTypeCount today = result.getLast();
        assertEquals(2, today.hit());
        assertEquals(1, today.miss());
        assertEquals(1, today.error());
        assertEquals(1, today.function());
    }

    // Bot filter (excludeBots) tests

    private static final String UA_CLAUDEBOT = "ClaudeBot/1.0";
    private static final String UA_GOOGLEBOT = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";

    @Test
    void uaGroupCounts_excludeBots_removesBotGroups() {
        Instant from = Instant.now();
        repository.saveEntries("logs/ua-groups-exclude-bots-test.gz", List.of(
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "Hit"),   // Browsers — kept
                entryWithUaAndResultType(UA_CLAUDEBOT,      "Hit"),   // AI Bots — excluded
                entryWithUaAndResultType(UA_GOOGLEBOT,      "Hit")    // Search Bots — excluded
        ));

        var withBots    = dashboardService.uaGroupCounts(from, Instant.now().plusSeconds(5), false);
        var withoutBots = dashboardService.uaGroupCounts(from, Instant.now().plusSeconds(5), true);

        assertTrue(withBots.stream().anyMatch(r -> "AI Bots".equals(r.name())));
        assertFalse(withoutBots.stream().anyMatch(r -> "AI Bots".equals(r.name())));
        assertFalse(withoutBots.stream().anyMatch(r -> "Search Bots".equals(r.name())));
        assertTrue(withoutBots.stream().anyMatch(r -> "Browsers".equals(r.name())));
    }

    @Test
    void topUserAgentsByResultType_excludeBots_removesBotEntries() {
        Instant from = Instant.now();
        repository.saveEntries("logs/ua-split-exclude-bots-test.gz", List.of(
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "Hit"),
                entryWithUaAndResultType(UA_CLAUDEBOT,      "Hit"),
                entryWithUaAndResultType(UA_GOOGLEBOT,      "Hit")
        ));

        var withoutBots = dashboardService.topUserAgentsByResultType(from, Instant.now().plusSeconds(5), 10, true);

        var names = withoutBots.stream().map(NameResultTypeCount::name).toList();
        assertTrue(names.contains("Chrome / Windows"));
        assertFalse(names.contains("ClaudeBot"));
        assertFalse(names.contains("Googlebot"));
    }

    @Test
    void topUserAgentsByResultType_excludeBots_removesNoUserAgent() {
        Instant from = Instant.now();
        repository.saveEntries("logs/ua-no-ua-exclude-test.gz", List.of(
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "Hit"),
                entryWithUaAndResultType(null,              "Hit")   // ua_name = "(no user agent)"
        ));

        var withoutBots = dashboardService.topUserAgentsByResultType(from, Instant.now().plusSeconds(5), 10, true);

        var names = withoutBots.stream().map(NameResultTypeCount::name).toList();
        assertTrue(names.contains("Chrome / Windows"));
        assertFalse(names.contains("(no user agent)"));
    }

    @Test
    void requestsPerDay_excludeBots_reducesDailyCounts() {
        Instant from = Instant.now();
        repository.saveEntries("logs/rpd-exclude-bots-test.gz", List.of(
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "Hit"),
                entryWithUaAndResultType(UA_CLAUDEBOT,      "Hit"),
                entryWithUaAndResultType(UA_CLAUDEBOT,      "Hit")
        ));

        var withBots    = dashboardService.requestsPerDay(from, Instant.now().plusSeconds(5), false);
        var withoutBots = dashboardService.requestsPerDay(from, Instant.now().plusSeconds(5), true);

        assertFalse(withBots.isEmpty());
        assertFalse(withoutBots.isEmpty());
        long totalWith    = withBots.stream().mapToLong(DailyResultTypeCount::hit).sum();
        long totalWithout = withoutBots.stream().mapToLong(DailyResultTypeCount::hit).sum();
        assertTrue(totalWith > totalWithout, "bot hits must be excluded from daily count");
    }

    @Test
    void requestsPerDay_excludeBots_excludesErrorResultType() {
        Instant from = Instant.now();
        repository.saveEntries("logs/rpd-exclude-error-test.gz", List.of(
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "Hit"),
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "Error"),
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "Error")
        ));

        var withFilter    = dashboardService.requestsPerDay(from, Instant.now().plusSeconds(5), true);
        var withoutFilter = dashboardService.requestsPerDay(from, Instant.now().plusSeconds(5), false);

        long errorsWith    = withFilter.stream().mapToLong(DailyResultTypeCount::error).sum();
        long errorsWithout = withoutFilter.stream().mapToLong(DailyResultTypeCount::error).sum();
        assertEquals(0, errorsWith, "Error rows must be excluded when filter is active");
        assertEquals(2, errorsWithout, "Error rows must remain when filter is inactive");
    }

    @Test
    void requestsPerDay_excludeBots_excludesFunctionResultType() {
        Instant from = Instant.now();
        repository.saveEntries("logs/rpd-exclude-function-test.gz", List.of(
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "Hit"),
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "FunctionGeneratedResponse"),
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "FunctionExecutionError")
        ));

        var withFilter    = dashboardService.requestsPerDay(from, Instant.now().plusSeconds(5), true);
        var withoutFilter = dashboardService.requestsPerDay(from, Instant.now().plusSeconds(5), false);

        long functionWith    = withFilter.stream().mapToLong(DailyResultTypeCount::function).sum();
        long functionWithout = withoutFilter.stream().mapToLong(DailyResultTypeCount::function).sum();
        assertEquals(0, functionWith, "Function rows must be excluded when filter is active");
        assertEquals(2, functionWithout, "Function rows must remain when filter is inactive");
    }

    // Real UA strings — ua_name is populated by UserAgentClassifier at insert time
    private static final String UA_CHROME_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String UA_FIREFOX_LINUX =
            "Mozilla/5.0 (X11; Linux x86_64; rv:147.0) Gecko/20100101 Firefox/147.0";

    @Test
    void topUserAgentsByResultType_countsPerResultType() {
        Instant from = Instant.now();
        repository.saveEntries("logs/ua-split-test.gz", List.of(
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "Hit"),
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "Hit"),
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "Miss"),
                entryWithUaAndResultType(UA_FIREFOX_LINUX, "Hit"),
                entryWithUaAndResultType(UA_FIREFOX_LINUX, "Error")
        ));

        List<NameResultTypeCount> result = dashboardService.topUserAgentsByResultType(
                from, Instant.now().plusSeconds(5), 10, false);

        assertFalse(result.isEmpty());
        NameResultTypeCount chrome = result.stream()
                .filter(r -> "Chrome / Windows".equals(r.name()))
                .findFirst().orElseThrow();
        assertEquals(2, chrome.hit());
        assertEquals(1, chrome.miss());
        assertEquals(0, chrome.error());

        NameResultTypeCount firefox = result.stream()
                .filter(r -> "Firefox / Linux".equals(r.name()))
                .findFirst().orElseThrow();
        assertEquals(1, firefox.hit());
        assertEquals(1, firefox.error());
    }

    @Test
    void uaResultTypes_countsPerType() {
        Instant from = Instant.now();
        repository.saveEntries("logs/ua-rt-test.gz", List.of(
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "Hit"),
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "Hit"),
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "Error"),
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "FunctionGeneratedResponse"),
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "FunctionExecutionError"),
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "FunctionThrottledError"),
                entryWithUaAndResultType(UA_FIREFOX_LINUX,  "Hit")  // different UA — must not appear
        ));

        List<NameCount> result = dashboardService.uaResultTypes(
                "Chrome / Windows", from, Instant.now().plusSeconds(5), false);

        assertEquals(2, result.stream().filter(n -> "Hit".equals(n.name())).findFirst().orElseThrow().count());
        assertEquals(1, result.stream().filter(n -> "Error".equals(n.name())).findFirst().orElseThrow().count());
        assertEquals(3, result.stream().filter(n -> "Function".equals(n.name())).findFirst().orElseThrow().count());
        assertTrue(result.stream().noneMatch(n -> "Miss".equals(n.name())));
        assertTrue(result.stream().noneMatch(n -> n.name().startsWith("Function") && !"Function".equals(n.name())));
    }

    @Test
    void uaCountries_resolvesIsoToDisplayName() {
        Instant from = Instant.now();
        repository.saveEntries("logs/ua-countries-test.gz", List.of(
                entryWithUaAndCountry(UA_CHROME_WINDOWS, "FR"),
                entryWithUaAndCountry(UA_CHROME_WINDOWS, "FR"),
                entryWithUaAndCountry(UA_CHROME_WINDOWS, "US"),
                entryWithUaAndCountry(UA_FIREFOX_LINUX,  "DE")  // different UA — must not appear
        ));

        List<NameCount> result = dashboardService.uaCountries(
                "Chrome / Windows", from, Instant.now().plusSeconds(5), false);

        assertEquals(2, result.stream().filter(n -> "France".equals(n.name())).findFirst().orElseThrow().count());
        assertEquals(1, result.stream().filter(n -> "United States".equals(n.name())).findFirst().orElseThrow().count());
        assertTrue(result.stream().noneMatch(n -> "Germany".equals(n.name())));
    }

    @Test
    void uaUrlsByResultType_excludesStaticAndFiltersToUa() {
        Instant from = Instant.now();
        repository.saveEntries("logs/ua-uri-test.gz", List.of(
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/index.html"),
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/index.html"),
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/style.css"),  // excluded extension
                entryWithUaAndUri(UA_FIREFOX_LINUX,  "/about.html")  // different UA — must not appear
        ));

        List<NameResultTypeCount> result = dashboardService.uaUrlsByResultType(
                "Chrome / Windows", from, Instant.now().plusSeconds(5), 10, false);

        var names = result.stream().map(NameResultTypeCount::name).toList();
        assertTrue(names.contains("/index.html"));
        assertFalse(names.contains("/style.css"));
        assertFalse(names.contains("/about.html"));
    }

    @Test
    void uaUrlsByResultType_aggregatesPhpUrlsUnderPhpLabel() {
        Instant from = Instant.now();
        repository.saveEntries("logs/ua-php-test.gz", List.of(
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/page.php"),
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/page.php"),
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/other.php"),
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/index.html")
        ));

        List<NameResultTypeCount> result = dashboardService.uaUrlsByResultType(
                "Chrome / Windows", from, Instant.now().plusSeconds(5), 10, false);

        var names = result.stream().map(NameResultTypeCount::name).toList();
        assertFalse(names.contains("/page.php"), "individual .php URLs must not appear");
        assertFalse(names.contains("/other.php"), "individual .php URLs must not appear");
        assertTrue(names.contains("PHP"), "PHP label must be present");
        var phpCount = result.stream().filter(n -> "PHP".equals(n.name())).mapToLong(NameResultTypeCount::total).sum();
        assertEquals(3, phpCount);
    }

    @Test
    void uaUrlsByResultType_aggregatesWpUrlsUnderWordPressLabel() {
        Instant from = Instant.now();
        repository.saveEntries("logs/ua-wp-test.gz", List.of(
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/wp-login.php"),    // matches /wp-% → WordPress, not PHP
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/wp-admin.php"),
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/wp-content/themes/style"),
                entryWithUaAndUri(UA_CHROME_WINDOWS, "//wp-login.php"),   // matches //wp-% → also WordPress
                entryWithUaAndUri(UA_CHROME_WINDOWS, "//wp-admin/"),
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/index.html")
        ));

        List<NameResultTypeCount> result = dashboardService.uaUrlsByResultType(
                "Chrome / Windows", from, Instant.now().plusSeconds(5), 10, false);

        var names = result.stream().map(NameResultTypeCount::name).toList();
        assertFalse(names.contains("/wp-login.php"), "individual /wp- URLs must not appear");
        assertFalse(names.contains("/wp-admin.php"), "individual /wp- URLs must not appear");
        assertFalse(names.contains("/wp-content/themes/style"), "individual /wp- URLs must not appear");
        assertFalse(names.contains("//wp-login.php"), "individual //wp- URLs must not appear");
        assertFalse(names.contains("//wp-admin/"), "individual //wp- URLs must not appear");
        assertTrue(names.contains("WordPress"), "WordPress label must be present");
        assertFalse(names.contains("PHP"), "/wp-*.php must go to WordPress, not PHP");
        var wpCount = result.stream().filter(n -> "WordPress".equals(n.name())).mapToLong(NameResultTypeCount::total).sum();
        assertEquals(5, wpCount);
    }

    @Test
    void uaUrlsByResultType_aggregatesNewWordPressPatternsUnderWordPressLabel() {
        Instant from = Instant.now();
        repository.saveEntries("logs/ua-wp-new-test.gz", List.of(
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/wordpress/page"),    // /wordpress/% → WordPress
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/wordpress/admin"),
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/wp/api"),            // /wp/% → WordPress
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/page.php7"),         // .php7 → PHP
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/PAGE.PHP7"),         // case-insensitive → PHP
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/index.html")
        ));

        List<NameResultTypeCount> result = dashboardService.uaUrlsByResultType(
                "Chrome / Windows", from, Instant.now().plusSeconds(5), 10, false);

        var names = result.stream().map(NameResultTypeCount::name).toList();
        assertTrue(names.contains("WordPress"), "WordPress label must be present");
        var wpCount = result.stream().filter(n -> "WordPress".equals(n.name())).mapToLong(NameResultTypeCount::total).sum();
        assertEquals(3, wpCount);

        assertTrue(names.contains("PHP"), "PHP label must be present for .php7");
        var phpCount = result.stream().filter(n -> "PHP".equals(n.name())).mapToLong(NameResultTypeCount::total).sum();
        assertEquals(2, phpCount);

        assertTrue(names.contains("/index.html"));
    }

    @Test
    void uaRequestsPerDay_countsPerResultType() {
        Instant from = Instant.now();
        repository.saveEntries("logs/ua-rpd-test.gz", List.of(
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "Hit"),
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "Hit"),
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "Miss"),
                entryWithUaAndResultType(UA_FIREFOX_LINUX,  "Hit")  // different UA — must not count
        ));

        List<DailyResultTypeCount> result = dashboardService.uaRequestsPerDay(
                "Chrome / Windows", from, Instant.now().plusSeconds(5), false);

        assertFalse(result.isEmpty());
        DailyResultTypeCount today = result.getLast();
        assertEquals(2, today.hit());
        assertEquals(1, today.miss());
        assertEquals(0, today.error());
    }

    @Test
    void topUrlsByResultType_aggregatesPhpAndWordPress() {
        Instant from = Instant.now();
        repository.saveEntries("logs/urls-split-grouping-test.gz", List.of(
                entryWithUri("/index.html"),
                entryWithUri("/index.html"),
                entryWithUri("/page.php"),
                entryWithUri("/other.php"),
                entryWithUri("/wp-login.php"),   // /wp-% wins over .php
                entryWithUri("/wp-content/themes/style"),
                entryWithUri("//wp-admin/")      // //wp-% also maps to WordPress
        ));

        var result = dashboardService.topUrlsByResultType(from, Instant.now().plusSeconds(5), 10, false);

        var names = result.stream().map(r -> r.name()).toList();
        assertTrue(names.contains("/index.html"));
        assertTrue(names.contains("PHP"));
        assertTrue(names.contains("WordPress"));
        assertFalse(names.contains("/page.php"));
        assertFalse(names.contains("/wp-login.php"));
        assertFalse(names.contains("//wp-admin/"));
        var phpTotal = result.stream().filter(r -> "PHP".equals(r.name()))
                .mapToLong(r -> r.hit() + r.miss() + r.function() + r.error()).sum();
        assertEquals(2, phpTotal);
        var wpTotal = result.stream().filter(r -> "WordPress".equals(r.name()))
                .mapToLong(r -> r.hit() + r.miss() + r.function() + r.error()).sum();
        assertEquals(3, wpTotal);
    }

    private CloudFrontLogEntry entryWithUaAndCountryAndResultType(String ua, String country, String resultType) {
        return new CloudFrontLogEntry(
                Instant.now(), "SFO53-P7", 1068L, "1.2.3.4", "GET",
                "/index.html", 200,
                null, ua,
                resultType, 336L, 0.001,
                resultType, "HTTP/1.1", 0.001, resultType,
                null, null, country
        );
    }

    private CloudFrontLogEntry entryWithUaAndCountry(String ua, String country) {
        return new CloudFrontLogEntry(
                Instant.now(), "SFO53-P7", 1068L, "1.2.3.4", "GET",
                "/index.html", 200,
                null, ua,
                "Hit", 336L, 0.001,
                "Hit", "HTTP/1.1", 0.001, "Hit",
                null, null, country
        );
    }

    private CloudFrontLogEntry entryWithUaAndUri(String ua, String uriStem) {
        return new CloudFrontLogEntry(
                Instant.now(), "SFO53-P7", 1068L, "1.2.3.4", "GET",
                uriStem, 200,
                null, ua,
                "Hit", 336L, 0.001,
                "Hit", "HTTP/1.1", 0.001, "Hit",
                null, null, "US"
        );
    }

    private CloudFrontLogEntry entry(String edgeLocation) {
        return new CloudFrontLogEntry(
                Instant.now(), edgeLocation, 1068L, "1.2.3.4", "GET",
                "/index.html", 200,
                null, "TestAgent/1.0",
                "Hit", 336L, 0.001,
                "Hit", "HTTP/1.1", 0.001, "Hit",
                null, null, "US"
        );
    }

    private CloudFrontLogEntry entryWithResultType(String resultType) {
        return new CloudFrontLogEntry(
                Instant.now(), "SFO53-P7", 1068L, "1.2.3.4", "GET",
                "/index.html", 200,
                null, "TestAgent/1.0",
                resultType, 336L, 0.001,
                resultType, "HTTP/1.1", 0.001, resultType,
                null, null, "US"
        );
    }

    private CloudFrontLogEntry entryWithUri(String uriStem) {
        return new CloudFrontLogEntry(
                Instant.now(), "SFO53-P7", 1068L, "1.2.3.4", "GET",
                uriStem, 200,
                null, "TestAgent/1.0",
                "Hit", 336L, 0.001,
                "Hit", "HTTP/1.1", 0.001, "Hit",
                null, null, "US"
        );
    }

    private CloudFrontLogEntry entryWithCountryAndUriAndResultType(String country, String uriStem, String resultType) {
        return new CloudFrontLogEntry(
                Instant.now(), "SFO53-P7", 1068L, "1.2.3.4", "GET",
                uriStem, 200,
                null, "TestAgent/1.0",
                resultType, 336L, 0.001,
                resultType, "HTTP/1.1", 0.001, resultType,
                null, null, country
        );
    }

    private CloudFrontLogEntry entryWithCountryAndResultType(String country, String resultType) {
        return new CloudFrontLogEntry(
                Instant.now(), "SFO53-P7", 1068L, "1.2.3.4", "GET",
                "/index.html", 200,
                null, "TestAgent/1.0",
                resultType, 336L, 0.001,
                resultType, "HTTP/1.1", 0.001, resultType,
                null, null, country
        );
    }

    private CloudFrontLogEntry entryWithUriAndResultType(String uriStem, String resultType) {
        return new CloudFrontLogEntry(
                Instant.now(), "SFO53-P7", 1068L, "1.2.3.4", "GET",
                uriStem, 200,
                null, "TestAgent/1.0",
                resultType, 336L, 0.001,
                resultType, "HTTP/1.1", 0.001, resultType,
                null, null, "US"
        );
    }

    // --- url-detail integration tests ---

    @Test
    void urlMatchingUriStems_singleUrl_returnsOnlyStem() {
        Instant from = Instant.now();
        repository.saveEntries("logs/url-stems-single-test.gz", List.of(
                entryWithUri("/index.html"),
                entryWithUri("/index.html"),
                entryWithUri("/about.html")   // different stem — must not appear
        ));

        List<NameCount> result = dashboardService.urlMatchingUriStems("/index.html", from, Instant.now().plusSeconds(5));

        assertEquals(1, result.size());
        assertEquals("/index.html", result.getFirst().name());
        assertEquals(2, result.getFirst().count());
    }

    @Test
    void urlMatchingUriStems_phpGroup_aggregatesPhpStems() {
        Instant from = Instant.now();
        repository.saveEntries("logs/url-stems-php-test.gz", List.of(
                entryWithUri("/page.php"),
                entryWithUri("/page.php"),
                entryWithUri("/other.php"),
                entryWithUri("/index.html")   // not PHP — must not appear
        ));

        List<NameCount> result = dashboardService.urlMatchingUriStems("PHP", from, Instant.now().plusSeconds(5));

        var names = result.stream().map(NameCount::name).toList();
        assertTrue(names.contains("/page.php"));
        assertTrue(names.contains("/other.php"));
        assertFalse(names.contains("/index.html"));
        long total = result.stream().mapToLong(NameCount::count).sum();
        assertEquals(3, total);
    }

    @Test
    void urlMatchingUriStems_wordPressGroup_aggregatesWpStems() {
        Instant from = Instant.now();
        repository.saveEntries("logs/url-stems-wp-test.gz", List.of(
                entryWithUri("/wp-login.php"),
                entryWithUri("/wp-content/themes/style"),
                entryWithUri("//wp-admin/"),
                entryWithUri("/index.html")   // not WordPress — must not appear
        ));

        List<NameCount> result = dashboardService.urlMatchingUriStems("WordPress", from, Instant.now().plusSeconds(5));

        var names = result.stream().map(NameCount::name).toList();
        assertTrue(names.contains("/wp-login.php"));
        assertTrue(names.contains("/wp-content/themes/style"));
        assertTrue(names.contains("//wp-admin/"));
        assertFalse(names.contains("/index.html"));
    }

    @Test
    void urlTopCountriesByResultType_singleUrl_filtersToStem() {
        Instant from = Instant.now();
        repository.saveEntries("logs/url-countries-test.gz", List.of(
                entryWithCountryAndUriAndResultType("FR", "/index.html", "Hit"),
                entryWithCountryAndUriAndResultType("FR", "/index.html", "Hit"),
                entryWithCountryAndUriAndResultType("US", "/index.html", "Miss"),
                entryWithCountryAndUriAndResultType("FR", "/about.html", "Hit")  // different stem
        ));

        var result = dashboardService.urlTopCountriesByResultType("/index.html", from, Instant.now().plusSeconds(5), 10);

        var fr = result.stream().filter(r -> "FR".equals(r.code())).findFirst().orElseThrow();
        assertEquals(2, fr.hit());
        var us = result.stream().filter(r -> "US".equals(r.code())).findFirst().orElseThrow();
        assertEquals(1, us.miss());
        // /about.html entry must not inflate FR count
        assertEquals(2, fr.hit() + fr.miss() + fr.function() + fr.error());
    }

    @Test
    void urlTopUserAgentsByResultType_phpGroup_aggregatesAllPhpStems() {
        Instant from = Instant.now();
        repository.saveEntries("logs/url-ua-php-test.gz", List.of(
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/page.php"),
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/other.php"),
                entryWithUaAndUri(UA_FIREFOX_LINUX,  "/page.php"),
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/index.html")  // not PHP
        ));

        var result = dashboardService.urlTopUserAgentsByResultType("PHP", from, Instant.now().plusSeconds(5), 10);

        var chrome = result.stream().filter(r -> "Chrome / Windows".equals(r.name())).findFirst().orElseThrow();
        assertEquals(2, chrome.hit());
        var firefox = result.stream().filter(r -> "Firefox / Linux".equals(r.name())).findFirst().orElseThrow();
        assertEquals(1, firefox.hit());
        // /index.html must not contribute
        long total = result.stream().mapToLong(r -> r.hit() + r.miss() + r.function() + r.error()).sum();
        assertEquals(3, total);
    }

    @Test
    void urlRequestsPerDay_singleUrl_countsPerResultType() {
        Instant from = Instant.now();
        repository.saveEntries("logs/url-rpd-test.gz", List.of(
                entryWithUriAndResultType("/index.html", "Hit"),
                entryWithUriAndResultType("/index.html", "Hit"),
                entryWithUriAndResultType("/index.html", "Miss"),
                entryWithUriAndResultType("/about.html", "Hit")  // different stem — must not count
        ));

        List<DailyResultTypeCount> result = dashboardService.urlRequestsPerDay(
                "/index.html", from, Instant.now().plusSeconds(5));

        assertFalse(result.isEmpty());
        DailyResultTypeCount today = result.getLast();
        assertEquals(2, today.hit());
        assertEquals(1, today.miss());
        assertEquals(0, today.error());
    }

    private CloudFrontLogEntry entryWithUaAndResultType(String ua, String resultType) {
        return new CloudFrontLogEntry(
                Instant.now(), "SFO53-P7", 1068L, "1.2.3.4", "GET",
                "/index.html", 200,
                null, ua,
                resultType, 336L, 0.001,
                resultType, "HTTP/1.1", 0.001, resultType,
                null, null, "US"
        );
    }

    private CloudFrontLogEntry entryWithReferer(String referer) {
        return new CloudFrontLogEntry(
                Instant.now(), "SFO53-P7", 1068L, "1.2.3.4", "GET",
                "/index.html", 200,
                referer, "TestAgent/1.0",
                "Hit", 336L, 0.001,
                "Hit", "HTTP/1.1", 0.001, "Hit",
                null, null, "US"
        );
    }

    @Test
    void uaGroupCounts_groupsByConfiguredGroups() {
        Instant from = Instant.now();
        repository.saveEntries("logs/ua-groups-test.gz", List.of(
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "Hit"),  // → Browsers (Chrome / Windows)
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "Hit"),  // → Browsers
                entryWithUaAndResultType(UA_FIREFOX_LINUX,  "Miss"), // → Browsers (Firefox / Linux)
                entryWithUaAndResultType("ClaudeBot/1.0",   "Hit"),  // → AI Bots (ua_name = "ClaudeBot")
                entryWithUaAndResultType(null,              "Hit")   // excluded (ua_name = "(no user agent)")
        ));

        var result = dashboardService.uaGroupCounts(from, Instant.now().plusSeconds(5), false);

        assertFalse(result.isEmpty());
        var browsers = result.stream().filter(r -> "Browsers".equals(r.name())).findFirst().orElseThrow();
        assertEquals(3, browsers.count());

        var aiBots = result.stream().filter(r -> "AI Bots".equals(r.name())).findFirst().orElseThrow();
        assertEquals(1, aiBots.count());

        // "(no user agent)" must be excluded entirely
        assertTrue(result.stream().noneMatch(r -> "Other".equals(r.name())));

        // sorted by count DESC
        assertTrue(result.get(0).count() >= result.get(result.size() - 1).count());
    }

    @Test
    void topReferers_normalizesSearchEngines() {
        Instant from = Instant.now();
        repository.saveEntries("logs/referers-search-test.gz", List.of(
                entryWithReferer("https://www.google.com/search?q=test"),
                entryWithReferer("https://www.google.com/search?q=other"),
                entryWithReferer("https://google.fr/search?q=test"),
                entryWithReferer("www.google.com/search?q=schemeless"),          // schemeless Google
                entryWithReferer("https://www.facebook.com/page"),
                entryWithReferer("https://www.babelio.com/livres/test"),
                entryWithReferer("https://www.qwant.com/?q=test"),
                entryWithReferer("https://www.duckduckgo.com/?q=test"),
                entryWithReferer("https://www.duckduckgo.com/?q=other"),
                entryWithReferer("https://www.bing.com/search?q=test"),
                entryWithReferer("https://fr.search.yahoo.com/search?p=test"),
                entryWithReferer("https://search.yahoo.com/search?p=other"),
                entryWithReferer("https://external.com/page")
        ));

        var result = dashboardService.topReferers(from, Instant.now().plusSeconds(5), 10, false);
        var map = result.stream().collect(java.util.stream.Collectors.toMap(NameCount::name, NameCount::count));

        assertEquals(4L, map.get("Google"), "all google.* referers including schemeless must be grouped");
        assertEquals(1L, map.get("Facebook"));
        assertEquals(1L, map.get("Babelio"));
        assertEquals(1L, map.get("Bing"));
        assertEquals(1L, map.get("Qwant"));
        assertEquals(2L, map.get("DuckDuckGo"));
        assertEquals(2L, map.get("Yahoo"), "all *.search.yahoo.com must be grouped");
        assertTrue(map.containsKey("https://external.com/page"), "unknown referers pass through unchanged");
    }

    @Test
    void topReferers_excludesSelfReferersAndNulls() {
        Instant from = Instant.now();
        repository.saveEntries("logs/referers-test.gz", List.of(
                entryWithReferer("https://external.com/page"),
                entryWithReferer("https://external.com/page"),
                entryWithReferer("https://other.org/"),
                entryWithReferer(null),                                          // null — excluded
                entryWithReferer("https://post-tenebras-lire.net/some-post"),    // https self with path — excluded
                entryWithReferer("http://post-tenebras-lire.net/some-post"),     // http self with path — excluded
                entryWithReferer("https://post-tenebras-lire.net"),               // https self bare domain — excluded
                entryWithReferer("post-tenebras-lire.net")                        // no-scheme self — excluded
        ));

        var result = dashboardService.topReferers(from, Instant.now().plusSeconds(5), 10, false);

        var names = result.stream().map(NameCount::name).toList();
        assertTrue(names.contains("https://external.com/page"));
        assertTrue(names.contains("https://other.org/"));
        assertFalse(names.contains("https://post-tenebras-lire.net/some-post"), "https self must be excluded");
        assertFalse(names.contains("http://post-tenebras-lire.net/some-post"),  "http self must be excluded");
        assertFalse(names.contains("https://post-tenebras-lire.net"),           "bare domain self must be excluded");
        assertFalse(names.contains("post-tenebras-lire.net"),                   "no-scheme self must be excluded");
        assertFalse(names.stream().anyMatch(n -> n == null), "null referers must be excluded");
        var extCount = result.stream()
                .filter(n -> "https://external.com/page".equals(n.name()))
                .mapToLong(NameCount::count).sum();
        assertEquals(2, extCount);
    }

    @Test
    void countryResultTypes_countsPerType() {
        Instant from = Instant.now();
        repository.saveEntries("logs/country-rt-test2.gz", List.of(
                entryWithUaAndCountryAndResultType(UA_CHROME_WINDOWS, "FR", "Hit"),
                entryWithUaAndCountryAndResultType(UA_CHROME_WINDOWS, "FR", "Hit"),
                entryWithUaAndCountryAndResultType(UA_CHROME_WINDOWS, "FR", "Miss"),
                entryWithUaAndCountryAndResultType(UA_CHROME_WINDOWS, "FR", "FunctionGeneratedResponse"),
                entryWithUaAndCountryAndResultType(UA_CHROME_WINDOWS, "FR", "FunctionExecutionError"),
                entryWithUaAndCountryAndResultType(UA_CHROME_WINDOWS, "US", "Hit")
        ));
        var result = dashboardService.countryResultTypes("FR", from, Instant.now().plusSeconds(5), false);
        assertEquals(2, result.stream().filter(n -> "Hit".equals(n.name())).findFirst().orElseThrow().count());
        assertEquals(1, result.stream().filter(n -> "Miss".equals(n.name())).findFirst().orElseThrow().count());
        assertEquals(2, result.stream().filter(n -> "Function".equals(n.name())).findFirst().orElseThrow().count());
        assertTrue(result.stream().noneMatch(n -> n.name().startsWith("Function") && !"Function".equals(n.name())));
    }

    @Test
    void countryRequestsPerDay_countsPerResultType() {
        Instant from = Instant.now();
        repository.saveEntries("logs/country-rpd-test2.gz", List.of(
                entryWithUaAndCountryAndResultType(UA_CHROME_WINDOWS, "FR", "Hit"),
                entryWithUaAndCountryAndResultType(UA_CHROME_WINDOWS, "FR", "Hit"),
                entryWithUaAndCountryAndResultType(UA_CHROME_WINDOWS, "FR", "Miss"),
                entryWithUaAndCountryAndResultType(UA_CHROME_WINDOWS, "US", "Hit")
        ));
        var result = dashboardService.countryRequestsPerDay("FR", from, Instant.now().plusSeconds(5), false);
        assertFalse(result.isEmpty());
        var today = result.getLast();
        assertEquals(2, today.hit());
        assertEquals(1, today.miss());
        assertEquals(0, today.error());
    }

    @Test
    void uaRawUserAgents_groupsByRawUaString() {
        Instant from = Instant.now();
        repository.saveEntries("logs/ua-raw-test.gz", List.of(
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "Hit"),
                entryWithUaAndResultType(UA_CHROME_WINDOWS, "Miss"),
                entryWithUaAndResultType(UA_FIREFOX_LINUX, "Hit")
        ));
        var result = dashboardService.uaRawUserAgents("Chrome / Windows", from, Instant.now().plusSeconds(5), false);
        assertFalse(result.isEmpty());
        var chrome = result.stream().filter(r -> UA_CHROME_WINDOWS.equals(r.name())).findFirst().orElseThrow();
        assertEquals(1, chrome.hit());
        assertEquals(1, chrome.miss());
        assertTrue(result.stream().noneMatch(r -> UA_FIREFOX_LINUX.equals(r.name())));
    }
}
