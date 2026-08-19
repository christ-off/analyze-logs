package com.example.analyzelog.model;

import java.time.LocalDate;

public record DailyCount(LocalDate day, long count) {}
