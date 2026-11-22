package com.example.analyzelog.model;

import java.time.LocalDate;

public record DailyFeedSubscribers(LocalDate day, String name, long count) {}
