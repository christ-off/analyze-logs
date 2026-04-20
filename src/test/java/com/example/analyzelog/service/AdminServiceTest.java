package com.example.analyzelog.service;

import com.example.analyzelog.model.StaticRefererEntry;
import com.example.analyzelog.model.StaticUaEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock JdbcTemplate jdbc;
    @Mock ReloadableClassifierService classifierService;
    @Mock ReloadableRefererService refererService;

    AdminService service;

    @BeforeEach
    void setUp() {
        service = new AdminService(jdbc, classifierService, refererService);
    }

    @Test
    void addUa_insertsAllFields() {
        service.addUa(new StaticUaEntry("TestBot", "AI Bots", "Test Bot", "TestBot/", 5));
        verify(jdbc).update(
                "INSERT INTO static_ua (ua_name, ua_group, ua_label, pattern, sort_order) VALUES (?, ?, ?, ?, ?)",
                "TestBot", "AI Bots", "Test Bot", "TestBot/", 5);
    }

    @Test
    void updateUaLabels_updatesGroupAndLabel() {
        service.updateUaLabels("TestBot", "Search Bots", "Test Bot v2");
        verify(jdbc).update("UPDATE static_ua SET ua_group = ?, ua_label = ? WHERE ua_name = ?",
                "Search Bots", "Test Bot v2", "TestBot");
    }

    @Test
    void updateUaClassifier_updatesPatternAndSortOrder() {
        service.updateUaClassifier("TestBot", "TestBot/", 10);
        verify(jdbc).update("UPDATE static_ua SET pattern = ?, sort_order = ? WHERE ua_name = ?",
                "TestBot/", 10, "TestBot");
    }

    @Test
    void deleteUa_deletesByUaName() {
        service.deleteUa("TestBot");
        verify(jdbc).update("DELETE FROM static_ua WHERE ua_name = ?", "TestBot");
    }

    @Test
    void addReferer_insertsRow() {
        service.addReferer(new StaticRefererEntry(0, "Google", null, "google.", null));
        verify(jdbc).update(
                "INSERT INTO static_referer (label, domain, domain_starts_with, domain_ends_with) VALUES (?, ?, ?, ?)",
                "Google", null, "google.", null);
    }

    @Test
    void addReferer_convertsBlankStringsToNull() {
        service.addReferer(new StaticRefererEntry(0, "Test", "", "  ", ""));
        verify(jdbc).update(
                "INSERT INTO static_referer (label, domain, domain_starts_with, domain_ends_with) VALUES (?, ?, ?, ?)",
                "Test", null, null, null);
    }

    @Test
    void updateReferer_updatesByRowid() {
        service.updateReferer(new StaticRefererEntry(42L, "Bing", "bing.com", null, null));
        verify(jdbc).update(
                "UPDATE static_referer SET label = ?, domain = ?, domain_starts_with = ?, domain_ends_with = ? WHERE rowid = ?",
                "Bing", "bing.com", null, null, 42L);
    }

    @Test
    void deleteReferer_deletesByRowid() {
        service.deleteReferer(7L);
        verify(jdbc).update("DELETE FROM static_referer WHERE rowid = ?", 7L);
    }

    @Test
    void reloadConfiguration_reloadsBothServices() {
        service.reloadConfiguration();
        verify(classifierService).reload();
        verify(refererService).reload();
    }

    @Test
    @SuppressWarnings("unchecked")
    void reclassifyLogs_classifiesDistinctUserAgents() {
        when(jdbc.queryForList("SELECT DISTINCT user_agent FROM cloudfront_logs", String.class))
                .thenReturn(List.of("Mozilla/5.0", "ClaudeBot/1.0"));
        when(classifierService.classify("Mozilla/5.0")).thenReturn("Chrome / Windows");
        when(classifierService.classify("ClaudeBot/1.0")).thenReturn("ClaudeBot");

        int count = service.reclassifyLogs();

        assertEquals(2, count);
        verify(jdbc).update("UPDATE cloudfront_logs SET ua_name = ? WHERE user_agent = ?",
                "Chrome / Windows", "Mozilla/5.0");
        verify(jdbc).update("UPDATE cloudfront_logs SET ua_name = ? WHERE user_agent = ?",
                "ClaudeBot", "ClaudeBot/1.0");
    }
}
