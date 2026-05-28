package com.example.analyzelog.model;

import java.time.LocalDate;

public record BotHumanDailyCount(LocalDate day, long bots, long humans) {
    public long total() { return bots + humans; }
}
