package com.example.analyzelog.service;

import com.example.analyzelog.model.AsnInfo;
import com.example.analyzelog.repository.AsnInfoRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class AsnInfoService {

    // quantcdn/as-metadata (CC0), updated daily. CSV columns: asn, handle, description, country-code.
    static final String CSV_URL = "https://raw.githubusercontent.com/quantcdn/asn-info/master/as.csv";

    private final RestClient restClient;
    private final AsnInfoRepository repository;

    public AsnInfoService(RestClient restClient, AsnInfoRepository repository) {
        this.restClient = restClient;
        this.repository = repository;
    }

    // Downloads and parses first, so a failed download never touches the stored names.
    public int refresh() {
        byte[] body = restClient.get().uri(CSV_URL).retrieve().body(byte[].class);
        if (body == null) {
            throw new IllegalStateException("Empty response from " + CSV_URL);
        }
        List<AsnInfo> entries = parseCsv(new String(body, StandardCharsets.UTF_8));
        if (entries.isEmpty()) {
            throw new IllegalStateException("No ASN found in " + CSV_URL);
        }
        repository.replaceAll(entries);
        return entries.size();
    }

    public int count() {
        return repository.count();
    }

    // RFC 4180: quoted fields may hold commas, doubled quotes and line breaks. The header row is skipped.
    static List<AsnInfo> parseCsv(String csv) {
        var rows = new ArrayList<List<String>>();
        var row = new ArrayList<String>();
        var field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (quoted) {
                if (c != '"') {
                    field.append(c);
                } else if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = false;
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') i++;
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }

        var entries = new ArrayList<AsnInfo>();
        for (var r : rows.stream().skip(1).toList()) {
            if (r.size() < 4 || !r.get(0).chars().allMatch(Character::isDigit) || r.get(0).isEmpty()) continue;
            entries.add(new AsnInfo(r.get(0), blankToNull(r.get(1)), blankToNull(r.get(2)), blankToNull(r.get(3)), null));
        }
        return entries;
    }

    private static String blankToNull(String s) {
        return s.isBlank() ? null : s;
    }
}
