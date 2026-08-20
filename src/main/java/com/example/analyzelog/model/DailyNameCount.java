package com.example.analyzelog.model;

import java.time.LocalDate;

public record DailyNameCount(LocalDate day, String name, long count) {}
