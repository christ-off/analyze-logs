package com.example.analyzelog.service;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
@DependsOn("liquibase")
public class ReloadableClassifierService {

    private final JdbcTemplate jdbc;
    private final AtomicReference<UserAgentClassifier> classifier = new AtomicReference<>();

    public ReloadableClassifierService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void load() {
        List<UaClassifierRule> rules = jdbc.query(
                "SELECT pattern, ua_name FROM static_ua WHERE pattern IS NOT NULL ORDER BY sort_order",
                (rs, _) -> new UaClassifierRule(rs.getString("pattern"), rs.getString("ua_name")));
        classifier.set(new UserAgentClassifier(rules));
    }

    public String classify(String ua) {
        return classifier.get().classify(ua);
    }

    public void reload() {
        load();
    }
}
