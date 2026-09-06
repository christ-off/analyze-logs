package com.example.analyzelog.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RobotsServiceParsingTest {

    @Test
    void parsesSimpleDisallowedAgent() {
        String robots = """
                User-agent: Googlebot
                Disallow: /private
                """;
        List<String> result = RobotsService.parseDisallowedAgents(robots);
        assertEquals(List.of("Googlebot"), result);
    }

    @Test
    void skipsWildcard() {
        String robots = """
                User-agent: *
                Disallow: /

                User-agent: Googlebot
                Disallow: /private
                """;
        List<String> result = RobotsService.parseDisallowedAgents(robots);
        assertFalse(result.contains("*"));
        assertTrue(result.contains("Googlebot"));
    }

    @Test
    void skipsAgentWithNoDisallow() {
        String robots = """
                User-agent: Googlebot
                Allow: /
                """;
        List<String> result = RobotsService.parseDisallowedAgents(robots);
        assertTrue(result.isEmpty());
    }

    @Test
    void skipsAgentWithEmptyDisallow() {
        String robots = """
                User-agent: Googlebot
                Disallow:
                """;
        List<String> result = RobotsService.parseDisallowedAgents(robots);
        assertTrue(result.isEmpty());
    }

    @Test
    void multipleAgentsInOneBlock() {
        String robots = """
                User-agent: BadBot
                User-agent: EvilBot
                Disallow: /
                """;
        List<String> result = RobotsService.parseDisallowedAgents(robots);
        assertTrue(result.contains("BadBot"));
        assertTrue(result.contains("EvilBot"));
    }

    @Test
    void multipleAgentsInOneBlockSkipsWildcard() {
        String robots = """
                User-agent: *
                User-agent: BadBot
                Disallow: /
                """;
        List<String> result = RobotsService.parseDisallowedAgents(robots);
        assertFalse(result.contains("*"));
        assertTrue(result.contains("BadBot"));
    }

    @Test
    void handlesComments() {
        String robots = """
                # This is a comment
                User-agent: SpamBot
                Disallow: /
                """;
        List<String> result = RobotsService.parseDisallowedAgents(robots);
        assertTrue(result.contains("SpamBot"));
    }

    @Test
    void handlesMultipleBlocks() {
        String robots = """
                User-agent: BotA
                Disallow: /secret

                User-agent: BotB
                Allow: /

                User-agent: BotC
                Disallow: /admin
                """;
        List<String> result = RobotsService.parseDisallowedAgents(robots);
        assertTrue(result.contains("BotA"));
        assertFalse(result.contains("BotB"));
        assertTrue(result.contains("BotC"));
    }

    @Test
    void returnsEmptyForNullInput() {
        assertTrue(RobotsService.parseDisallowedAgents(null).isEmpty());
    }

    @Test
    void returnsEmptyForEmptyInput() {
        assertTrue(RobotsService.parseDisallowedAgents("").isEmpty());
    }
}