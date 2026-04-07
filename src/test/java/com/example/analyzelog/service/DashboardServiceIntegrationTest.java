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
    void topAllowedUriStems_excludesStaticExtensions() {
        Instant from = Instant.now();
        repository.saveEntries("logs/uri-filter-test.gz", List.of(
                entryWithUri("/index.html"),
                entryWithUri("/about.html"),
                entryWithUri("/about.html"),
                entryWithUri("/style.css"),
                entryWithUri("/app.js"),
                entryWithUri("/logo.png"),
                entryWithUri("/icon.svg")
        ));

        var result = dashboardService.topAllowedUriStems(from, Instant.now().plusSeconds(5), 10);

        var names = result.stream().map(nc -> nc.name()).toList();
        assertTrue(names.contains("/about.html"));
        assertTrue(names.contains("/index.html"));
        assertFalse(names.contains("/style.css"));
        assertFalse(names.contains("/app.js"));
        assertFalse(names.contains("/logo.png"));
        assertFalse(names.contains("/icon.svg"));
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

        var names = result.stream().map(nc -> nc.name()).toList();
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
                entryWithResultType("Redirect"),
                entryWithResultType("FunctionExecutionError")
        ));

        List<DailyResultTypeCount> result = dashboardService.requestsPerDay(
                from, Instant.now().plusSeconds(5));

        assertFalse(result.isEmpty());
        DailyResultTypeCount today = result.getLast();
        assertEquals(2, today.hit());
        assertEquals(1, today.miss());
        assertEquals(1, today.error());
        assertEquals(1, today.redirect());
        assertEquals(1, today.function());
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
                from, Instant.now().plusSeconds(5), 10);

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
                entryWithUaAndResultType(UA_FIREFOX_LINUX,  "Hit")  // different UA — must not appear
        ));

        List<NameCount> result = dashboardService.uaResultTypes(
                "Chrome / Windows", from, Instant.now().plusSeconds(5));

        assertEquals(2, result.stream().filter(n -> "Hit".equals(n.name())).findFirst().orElseThrow().count());
        assertEquals(1, result.stream().filter(n -> "Error".equals(n.name())).findFirst().orElseThrow().count());
        assertTrue(result.stream().noneMatch(n -> "Miss".equals(n.name())));
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
                "Chrome / Windows", from, Instant.now().plusSeconds(5));

        assertEquals(2, result.stream().filter(n -> "France".equals(n.name())).findFirst().orElseThrow().count());
        assertEquals(1, result.stream().filter(n -> "United States".equals(n.name())).findFirst().orElseThrow().count());
        assertTrue(result.stream().noneMatch(n -> "Germany".equals(n.name())));
    }

    @Test
    void uaUriStems_excludesStaticAndFiltersToUa() {
        Instant from = Instant.now();
        repository.saveEntries("logs/ua-uri-test.gz", List.of(
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/index.html"),
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/index.html"),
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/style.css"),  // excluded extension
                entryWithUaAndUri(UA_FIREFOX_LINUX,  "/about.html")  // different UA — must not appear
        ));

        List<NameCount> result = dashboardService.uaUriStems(
                "Chrome / Windows", from, Instant.now().plusSeconds(5), 10);

        var names = result.stream().map(NameCount::name).toList();
        assertTrue(names.contains("/index.html"));
        assertFalse(names.contains("/style.css"));
        assertFalse(names.contains("/about.html"));
    }

    @Test
    void uaUriStems_aggregatesPhpUrlsUnderPhpLabel() {
        Instant from = Instant.now();
        repository.saveEntries("logs/ua-php-test.gz", List.of(
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/page.php"),
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/page.php"),
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/other.php"),
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/index.html")
        ));

        List<NameCount> result = dashboardService.uaUriStems(
                "Chrome / Windows", from, Instant.now().plusSeconds(5), 10);

        var names = result.stream().map(NameCount::name).toList();
        assertFalse(names.contains("/page.php"), "individual .php URLs must not appear");
        assertFalse(names.contains("/other.php"), "individual .php URLs must not appear");
        assertTrue(names.contains("PHP"), "PHP label must be present");
        var phpCount = result.stream().filter(n -> "PHP".equals(n.name())).mapToLong(NameCount::count).sum();
        assertEquals(3, phpCount);
    }

    @Test
    void uaUriStems_aggregatesWpUrlsUnderWordpressLabel() {
        Instant from = Instant.now();
        repository.saveEntries("logs/ua-wp-test.gz", List.of(
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/wp-login.php"),    // matches /wp-% → Wordpress, not PHP
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/wp-admin.php"),
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/wp-content/themes/style"),
                entryWithUaAndUri(UA_CHROME_WINDOWS, "//wp-login.php"),   // matches //wp-% → also Wordpress
                entryWithUaAndUri(UA_CHROME_WINDOWS, "//wp-admin/"),
                entryWithUaAndUri(UA_CHROME_WINDOWS, "/index.html")
        ));

        List<NameCount> result = dashboardService.uaUriStems(
                "Chrome / Windows", from, Instant.now().plusSeconds(5), 10);

        var names = result.stream().map(NameCount::name).toList();
        assertFalse(names.contains("/wp-login.php"), "individual /wp- URLs must not appear");
        assertFalse(names.contains("/wp-admin.php"), "individual /wp- URLs must not appear");
        assertFalse(names.contains("/wp-content/themes/style"), "individual /wp- URLs must not appear");
        assertFalse(names.contains("//wp-login.php"), "individual //wp- URLs must not appear");
        assertFalse(names.contains("//wp-admin/"), "individual //wp- URLs must not appear");
        assertTrue(names.contains("Wordpress"), "Wordpress label must be present");
        assertFalse(names.contains("PHP"), "/wp-*.php must go to Wordpress, not PHP");
        var wpCount = result.stream().filter(n -> "Wordpress".equals(n.name())).mapToLong(NameCount::count).sum();
        assertEquals(5, wpCount);
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
                "Chrome / Windows", from, Instant.now().plusSeconds(5));

        assertFalse(result.isEmpty());
        DailyResultTypeCount today = result.getLast();
        assertEquals(2, today.hit());
        assertEquals(1, today.miss());
        assertEquals(0, today.error());
    }

    @Test
    void topUriStems_aggregatesPhpAndWordpress() {
        Instant from = Instant.now();
        repository.saveEntries("logs/top-uri-stems-test.gz", List.of(
                entryWithUri("/index.html"),
                entryWithUri("/index.html"),
                entryWithUri("/page.php"),
                entryWithUri("/other.php"),
                entryWithUri("/wp-login.php"),   // /wp-% wins over .php
                entryWithUri("/wp-content/themes/style"),
                entryWithUri("//wp-admin/")      // //wp-% also maps to Wordpress
        ));

        var result = dashboardService.topUriStems(from, Instant.now().plusSeconds(5), 10);

        var names = result.stream().map(NameCount::name).toList();
        assertTrue(names.contains("/index.html"));
        assertTrue(names.contains("PHP"));
        assertTrue(names.contains("Wordpress"));
        assertFalse(names.contains("/page.php"));
        assertFalse(names.contains("/wp-login.php"));
        assertFalse(names.contains("//wp-admin/"));
        var phpCount = result.stream().filter(n -> "PHP".equals(n.name())).mapToLong(NameCount::count).sum();
        assertEquals(2, phpCount);
        var wpCount = result.stream().filter(n -> "Wordpress".equals(n.name())).mapToLong(NameCount::count).sum();
        assertEquals(3, wpCount);
    }

    private CloudFrontLogEntry entryWithUaAndCountry(String ua, String country) {
        return new CloudFrontLogEntry(
                Instant.now(), "SFO53-P7", 1068L, "1.2.3.4", "GET",
                "/index.html", 200,
                null, ua,
                "Hit", "https", 336L, 0.001,
                "Hit", "HTTP/1.1", 0.001, "Hit",
                null, null, country
        );
    }

    private CloudFrontLogEntry entryWithUaAndUri(String ua, String uriStem) {
        return new CloudFrontLogEntry(
                Instant.now(), "SFO53-P7", 1068L, "1.2.3.4", "GET",
                uriStem, 200,
                null, ua,
                "Hit", "https", 336L, 0.001,
                "Hit", "HTTP/1.1", 0.001, "Hit",
                null, null, "US"
        );
    }

    private CloudFrontLogEntry entry(String edgeLocation) {
        return new CloudFrontLogEntry(
                Instant.now(), edgeLocation, 1068L, "1.2.3.4", "GET",
                "/index.html", 200,
                null, "TestAgent/1.0",
                "Hit", "https", 336L, 0.001,
                "Hit", "HTTP/1.1", 0.001, "Hit",
                null, null, "US"
        );
    }

    private CloudFrontLogEntry entryWithResultType(String resultType) {
        return new CloudFrontLogEntry(
                Instant.now(), "SFO53-P7", 1068L, "1.2.3.4", "GET",
                "/index.html", 200,
                null, "TestAgent/1.0",
                resultType, "https", 336L, 0.001,
                resultType, "HTTP/1.1", 0.001, resultType,
                null, null, "US"
        );
    }

    private CloudFrontLogEntry entryWithUri(String uriStem) {
        return new CloudFrontLogEntry(
                Instant.now(), "SFO53-P7", 1068L, "1.2.3.4", "GET",
                uriStem, 200,
                null, "TestAgent/1.0",
                "Hit", "https", 336L, 0.001,
                "Hit", "HTTP/1.1", 0.001, "Hit",
                null, null, "US"
        );
    }

    private CloudFrontLogEntry entryWithUaAndResultType(String ua, String resultType) {
        return new CloudFrontLogEntry(
                Instant.now(), "SFO53-P7", 1068L, "1.2.3.4", "GET",
                "/index.html", 200,
                null, ua,
                resultType, "https", 336L, 0.001,
                resultType, "HTTP/1.1", 0.001, resultType,
                null, null, "US"
        );
    }

    private CloudFrontLogEntry entryWithReferer(String referer) {
        return new CloudFrontLogEntry(
                Instant.now(), "SFO53-P7", 1068L, "1.2.3.4", "GET",
                "/index.html", 200,
                referer, "TestAgent/1.0",
                "Hit", "https", 336L, 0.001,
                "Hit", "HTTP/1.1", 0.001, "Hit",
                null, null, "US"
        );
    }

    @Test
    void topReferers_excludesSelfReferersAndNulls() {
        Instant from = Instant.now();
        repository.saveEntries("logs/referers-test.gz", List.of(
                entryWithReferer("https://external.com/page"),
                entryWithReferer("https://external.com/page"),
                entryWithReferer("https://other.org/"),
                entryWithReferer(null),                                          // null — excluded
                entryWithReferer("https://post-tenebras-lire.net/some-post"),    // https self — excluded
                entryWithReferer("http://post-tenebras-lire.net/some-post")      // http self — excluded
        ));

        var result = dashboardService.topReferers(from, Instant.now().plusSeconds(5), 10);

        var names = result.stream().map(NameCount::name).toList();
        assertTrue(names.contains("https://external.com/page"));
        assertTrue(names.contains("https://other.org/"));
        assertFalse(names.contains("https://post-tenebras-lire.net/some-post"), "https self must be excluded");
        assertFalse(names.contains("http://post-tenebras-lire.net/some-post"),  "http self must be excluded");
        assertFalse(names.stream().anyMatch(n -> n == null), "null referers must be excluded");
        var extCount = result.stream()
                .filter(n -> "https://external.com/page".equals(n.name()))
                .mapToLong(NameCount::count).sum();
        assertEquals(2, extCount);
    }
}
