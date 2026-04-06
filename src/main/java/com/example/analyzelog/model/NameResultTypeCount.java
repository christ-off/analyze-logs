package com.example.analyzelog.model;

public record NameResultTypeCount(String name, long hit, long miss, long function, long error, long redirect) {}
