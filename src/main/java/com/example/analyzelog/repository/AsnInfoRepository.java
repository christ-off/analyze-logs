package com.example.analyzelog.repository;

import com.example.analyzelog.model.AsnInfo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class AsnInfoRepository {

    private final JdbcTemplate jdbc;

    public AsnInfoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Replaces the whole table in one transaction: a failure leaves the previous names in place.
    @Transactional
    public void replaceAll(List<AsnInfo> entries) {
        jdbc.update("DELETE FROM asn_info");
        jdbc.batchUpdate(
                "INSERT OR REPLACE INTO asn_info (asn, handle, name, country_code, category) VALUES (?,?,?,?,?)",
                entries.stream()
                        .map(e -> new Object[]{e.asn(), e.handle(), e.name(), e.countryCode(), e.category()})
                        .toList());
    }

    public int count() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM asn_info", Integer.class);
        return n == null ? 0 : n;
    }
}
