package com.example.analyzelog.model;

import java.time.LocalDate;

public record DailyResultTypeCount(LocalDate day, long hit, long miss, long function, long error, long redirect) {}
