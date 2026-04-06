package com.example.analyzelog.service;

import com.example.analyzelog.model.CloudFrontLogEntry;
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
    }

    @Autowired
    LogRepository repository;

    @Autowired
    DashboardService dashboardService;

    @Test
    void topEdgeLocations_resolvesToHumanReadable() {
        repository.saveEntries("logs/edge-test.gz", List.of(
                entry("SFO53-P7"),
                entry("SFO53-P7"),
                entry("CDG55-P2")
        ));

        var result = dashboardService.topEdgeLocations(
                Instant.EPOCH, Instant.now().plusSeconds(3600), 10);

        assertFalse(result.isEmpty());
        var top = result.getFirst();
        assertEquals("San Francisco, United States", top.name());
        assertEquals(2, top.count());
    }

    @Test
    void topEdgeLocations_secondEntryIsResolved() {
        repository.saveEntries("logs/edge-test-2.gz", List.of(
                entry("CDG55-P2"),
                entry("LHR5-P1")
        ));

        var result = dashboardService.topEdgeLocations(
                Instant.EPOCH, Instant.now().plusSeconds(3600), 10);

        var names = result.stream().map(nc -> nc.name()).toList();
        assertTrue(names.stream().anyMatch(n -> n.contains("Paris")));
        assertTrue(names.stream().anyMatch(n -> n.contains("London")));
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
}
