package com.example.analyzelog.model;

public record DisobedientBot(String userAgent, long count, long hit, long miss, long error, long function) {}