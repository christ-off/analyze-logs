package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.DateRange;
import com.example.analyzelog.service.DashboardService;

abstract class DetailControllerBase {

    protected final DashboardService dashboardService;
    protected final AppProperties appProperties;

    protected DetailControllerBase(DashboardService dashboardService, AppProperties appProperties) {
        this.dashboardService = dashboardService;
        this.appProperties = appProperties;
    }

    protected DateRange requestRange(String range, String from, String to) {
        if (from != null && to != null) return DateRange.fromParams(from, to);
        return switch (range != null ? range : "7d") {
            case "1d"  -> DateRange.lastDays(1);
            case "30d" -> DateRange.lastDays(30);
            case "3m"  -> DateRange.lastMonths(3);
            default    -> DateRange.lastDays(7);
        };
    }
}
