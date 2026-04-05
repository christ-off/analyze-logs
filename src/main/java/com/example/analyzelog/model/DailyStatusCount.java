package com.example.analyzelog.model;

import java.time.LocalDate;

public record DailyStatusCount(LocalDate day, long success, long clientError, long serverError) {}