package com.example.analyzelog.service;

import com.example.analyzelog.config.UaGroupProperties;

import java.util.Collection;
import java.util.List;

public class UaGroupClassifier {

    private static final String OTHER = "Other";

    private final List<UaGroupProperties.Group> groups;

    public UaGroupClassifier(UaGroupProperties properties) {
        this.groups = properties.groups();
    }

    public List<String> labelsForGroups(Collection<String> groupNames) {
        return groups.stream()
                .filter(g -> groupNames.contains(g.name()))
                .flatMap(g -> g.labels() == null ? java.util.stream.Stream.empty() : g.labels().stream())
                .toList();
    }

    public List<String> prefixesForGroups(Collection<String> groupNames) {
        return groups.stream()
                .filter(g -> groupNames.contains(g.name()))
                .flatMap(g -> g.labelPrefixes() == null ? java.util.stream.Stream.empty() : g.labelPrefixes().stream())
                .toList();
    }

    public String classify(String uaName) {
        if (uaName == null) return OTHER;
        for (var group : groups) {
            List<String> labels = group.labels();
            if (labels != null && labels.contains(uaName)) return group.name();
            List<String> prefixes = group.labelPrefixes();
            if (prefixes != null) {
                for (String prefix : prefixes) {
                    if (uaName.startsWith(prefix)) return group.name();
                }
            }
        }
        return OTHER;
    }
}
