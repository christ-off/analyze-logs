package com.example.analyzelog.model;

public record BotHumanHourlyCount(int hour, long bots, long humans) {
    public long total() { return bots + humans; }
}
