package com.example.analyzelog.service;

import com.example.analyzelog.config.UaGroupProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UaGroupClassifierTest {

    private UaGroupClassifier classifier;

    @BeforeEach
    void setUp() {
        var props = new UaGroupProperties(List.of(
                new UaGroupProperties.Group("AI Bots",    List.of("ClaudeBot", "OAI-SearchBot"), null),
                new UaGroupProperties.Group("Browsers",   List.of("Unknown"),   List.of("Chrome / ", "Firefox / ")),
                new UaGroupProperties.Group("Feed Readers", List.of("Feedly", "Feedbin"), null)
        ));
        classifier = new UaGroupClassifier(props);
    }

    @Test
    void exactLabelMatchReturnsGroup() {
        assertEquals("AI Bots",      classifier.classify("ClaudeBot"));
        assertEquals("AI Bots",      classifier.classify("OAI-SearchBot"));
        assertEquals("Feed Readers", classifier.classify("Feedly"));
        assertEquals("Feed Readers", classifier.classify("Feedbin"));
    }

    @Test
    void prefixMatchReturnsGroup() {
        assertEquals("Browsers", classifier.classify("Chrome / Windows"));
        assertEquals("Browsers", classifier.classify("Chrome / Linux"));
        assertEquals("Browsers", classifier.classify("Firefox / macOS"));
    }

    @Test
    void exactMatchTakesPriorityOverPrefix() {
        // "Unknown" is an exact label in Browsers
        assertEquals("Browsers", classifier.classify("Unknown"));
    }

    @Test
    void unmatchedLabelReturnsOther() {
        assertEquals("Other", classifier.classify("SomeBotNotConfigured"));
        assertEquals("Other", classifier.classify("(no user agent)"));
    }

    @Test
    void nullReturnsOther() {
        assertEquals("Other", classifier.classify(null));
    }

    @Test
    void firstGroupWins() {
        // "ClaudeBot" is in AI Bots (first), not in any other group
        assertEquals("AI Bots", classifier.classify("ClaudeBot"));
    }

    @Test
    void groupWithNullLabelsAndPrefixesHandledGracefully() {
        var props = new UaGroupProperties(List.of(
                new UaGroupProperties.Group("Only Prefixes", null, List.of("Safari / ")),
                new UaGroupProperties.Group("Only Labels",   List.of("Obsidian"), null)
        ));
        var c = new UaGroupClassifier(props);
        assertEquals("Only Prefixes", c.classify("Safari / iPhone"));
        assertEquals("Only Labels",   c.classify("Obsidian"));
        assertEquals("Other",         c.classify("Unknown"));
    }
}