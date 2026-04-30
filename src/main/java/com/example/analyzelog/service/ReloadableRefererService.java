package com.example.analyzelog.service;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
@DependsOn("liquibase")
public class ReloadableRefererService {

    private final JdbcTemplate jdbc;
    private final AtomicReference<List<RefererRule>> rules = new AtomicReference<>();

    public ReloadableRefererService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void load() {
        rules.set(jdbc.query(
                "SELECT label, domain, domain_starts_with, domain_ends_with FROM static_referer",
                (rs, _) -> new RefererRule(
                        rs.getString("label"),
                        rs.getString("domain"),
                        rs.getString("domain_starts_with"),
                        rs.getString("domain_ends_with"))));
    }

    public List<RefererRule> getRules() {
        return rules.get();
    }

    public void reload() {
        load();
    }
}
