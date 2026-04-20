package com.example.analyzelog.service;

import com.example.analyzelog.model.StaticRefererEntry;
import com.example.analyzelog.model.StaticUaEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

@SuppressWarnings("java:S2077") // all dynamic SQL uses only column names from trusted constants, not user input
@Service
public class AdminService {

    private final JdbcTemplate jdbc;
    private final ReloadableClassifierService classifierService;
    private final ReloadableRefererService refererService;

    public AdminService(JdbcTemplate jdbc, ReloadableClassifierService classifierService,
                        ReloadableRefererService refererService) {
        this.jdbc = jdbc;
        this.classifierService = classifierService;
        this.refererService = refererService;
    }

    // --- static_ua ---

    public List<StaticUaEntry> allUa() {
        return jdbc.query(
                "SELECT ua_name, ua_group, ua_label, pattern, sort_order FROM static_ua ORDER BY ua_group, ua_label",
                (rs, _) -> new StaticUaEntry(
                        rs.getString("ua_name"),
                        rs.getString("ua_group"),
                        rs.getString("ua_label"),
                        rs.getString("pattern"),
                        (Integer) rs.getObject("sort_order")));
    }

    public void addUa(StaticUaEntry entry) {
        jdbc.update(
                "INSERT INTO static_ua (ua_name, ua_group, ua_label, pattern, sort_order) VALUES (?, ?, ?, ?, ?)",
                entry.uaName(), entry.uaGroup(), entry.uaLabel(), entry.pattern(), entry.sortOrder());
    }

    public void updateUaLabels(String uaName, String uaGroup, String uaLabel) {
        jdbc.update("UPDATE static_ua SET ua_group = ?, ua_label = ? WHERE ua_name = ?",
                uaGroup, uaLabel, uaName);
    }

    public void updateUaClassifier(String uaName, String pattern, Integer sortOrder) {
        jdbc.update("UPDATE static_ua SET pattern = ?, sort_order = ? WHERE ua_name = ?",
                pattern, sortOrder, uaName);
    }

    public void deleteUa(String uaName) {
        jdbc.update("DELETE FROM static_ua WHERE ua_name = ?", uaName);
    }

    // --- static_referer (rowid used as identifier) ---

    public List<StaticRefererEntry> allReferers() {
        return jdbc.query(
                "SELECT rowid as id, label, domain, domain_starts_with, domain_ends_with FROM static_referer ORDER BY label",
                (rs, _) -> new StaticRefererEntry(
                        rs.getLong("id"),
                        rs.getString("label"),
                        rs.getString("domain"),
                        rs.getString("domain_starts_with"),
                        rs.getString("domain_ends_with")));
    }

    public void addReferer(StaticRefererEntry entry) {
        jdbc.update(
                "INSERT INTO static_referer (label, domain, domain_starts_with, domain_ends_with) VALUES (?, ?, ?, ?)",
                entry.label(), nullIfBlank(entry.domain()), nullIfBlank(entry.domainStartsWith()), nullIfBlank(entry.domainEndsWith()));
    }

    public void updateReferer(StaticRefererEntry entry) {
        jdbc.update(
                "UPDATE static_referer SET label = ?, domain = ?, domain_starts_with = ?, domain_ends_with = ? WHERE rowid = ?",
                entry.label(), nullIfBlank(entry.domain()), nullIfBlank(entry.domainStartsWith()), nullIfBlank(entry.domainEndsWith()), entry.id());
    }

    public void deleteReferer(long id) {
        jdbc.update("DELETE FROM static_referer WHERE rowid = ?", id);
    }

    // --- Config reload ---

    public void reloadConfiguration() {
        classifierService.reload();
        refererService.reload();
    }

    // --- Re-classify existing cloudfront_logs ---

    @Transactional
    public int reclassifyLogs() {
        List<String> userAgents = jdbc.queryForList(
                "SELECT DISTINCT user_agent FROM cloudfront_logs", String.class);
        for (String ua : userAgents) {
            String newName = classifierService.classify(ua);
            jdbc.update("UPDATE cloudfront_logs SET ua_name = ? WHERE user_agent = ?", newName, ua);
        }
        return userAgents.size();
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
