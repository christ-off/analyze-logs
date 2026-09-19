package com.example.analyzelog.web;

import java.util.Locale;

// The browsers with a dedicated dashboard. label is the ua_name prefix ("Chrome / Windows", ...).
enum Browser {
    CHROME("Chrome"), EDGE("Edge"), FIREFOX("Firefox"), SAFARI("Safari");

    // Path-variable regex matching exactly the keys below — keep in sync with the constants.
    static final String KEYS = "chrome|edge|firefox|safari";

    private final String label;

    Browser(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }

    String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Browser fromKey(String key) {
        return valueOf(key.toUpperCase(Locale.ROOT));
    }
}
