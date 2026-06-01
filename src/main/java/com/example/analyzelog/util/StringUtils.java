package com.example.analyzelog.util;

public final class StringUtils {

    private StringUtils() {}

    public static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}