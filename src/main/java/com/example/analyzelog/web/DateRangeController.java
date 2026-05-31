package com.example.analyzelog.web;

import com.example.analyzelog.model.DateRange;
import org.springframework.ui.Model;

abstract class DateRangeController {

    protected void addDateAttributes(Model model, DateRange dateRange, String activeRange) {
        model.addAttribute("from", dateRange.fromIso());
        model.addAttribute("to", dateRange.toIso());
        model.addAttribute("fromDate", dateRange.fromDate().toString());
        model.addAttribute("toDate", dateRange.toDate().toString());
        model.addAttribute("activeRange", activeRange);
    }

    protected String resolveActiveRange(String range, String from, String to) {
        if (from != null && to != null) return "custom";
        return range != null ? range : "7d";
    }

    protected DateRange resolveRange(String range, String from, String to) {
        if (from != null && to != null) return DateRange.fromParams(from, to);
        return switch (range != null ? range : "7d") {
            case "1d"  -> DateRange.lastDays(1);
            case "30d" -> DateRange.lastDays(30);
            case "3m"  -> DateRange.lastMonths(3);
            default    -> DateRange.lastDays(7);
        };
    }
}
