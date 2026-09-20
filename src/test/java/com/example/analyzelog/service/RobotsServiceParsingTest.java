package com.example.analyzelog.service;

import com.example.analyzelog.service.RobotsService.RobotsRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RobotsServiceParsingTest {

    private static final String ALLOW_LIST_ROBOTS = """
            Sitemap: https://example.com/sitemap.xml

            # Allowed search engines.
            User-agent: Googlebot
            User-agent: Bingbot
            Allow: /
            Disallow: /assets
            Disallow: /pagefind

            # Everyone else is blocked
            User-agent: *
            Disallow: /
            """;

    @Test
    void parsesAllowListWithDisallowedPathsAndWildcard() {
        assertEquals(List.of(
                new RobotsRule("Googlebot", "/assets"),
                new RobotsRule("Googlebot", "/pagefind"),
                new RobotsRule("Bingbot", "/assets"),
                new RobotsRule("Bingbot", "/pagefind"),
                new RobotsRule("*", "/")),
                RobotsService.parseRules(ALLOW_LIST_ROBOTS));
    }

    @Test
    void namedGroupWithoutDisallowGetsEmptyPath() {
        String robots = """
                User-agent: Googlebot
                Allow: /
                """;
        assertEquals(List.of(new RobotsRule("Googlebot", "")), RobotsService.parseRules(robots));
    }

    @Test
    void emptyDisallowMeansNothingDisallowed() {
        String robots = """
                User-agent: Googlebot
                Disallow:
                """;
        assertEquals(List.of(new RobotsRule("Googlebot", "")), RobotsService.parseRules(robots));
    }

    @Test
    void ignoresCommentsSitemapAndCase() {
        String robots = """
                # comment
                user-agent: Googlebot
                DISALLOW: /private
                """;
        assertEquals(List.of(new RobotsRule("Googlebot", "/private")), RobotsService.parseRules(robots));
    }

    @Test
    void handlesWindowsLineEndings() {
        String robots = "User-agent: A\r\nDisallow: /a\r\n\r\nUser-agent: B\r\nDisallow: /b\r\n";
        assertEquals(List.of(new RobotsRule("A", "/a"), new RobotsRule("B", "/b")), RobotsService.parseRules(robots));
    }

    @Test
    void deduplicatesRules() {
        String robots = """
                User-agent: A
                Disallow: /x

                User-agent: A
                Disallow: /x
                """;
        assertEquals(List.of(new RobotsRule("A", "/x")), RobotsService.parseRules(robots));
    }

    @Test
    void nullOrBlankReturnsEmpty() {
        assertTrue(RobotsService.parseRules(null).isEmpty());
        assertTrue(RobotsService.parseRules("").isEmpty());
    }
}
