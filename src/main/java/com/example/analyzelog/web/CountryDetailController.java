package com.example.analyzelog.web;

import com.example.analyzelog.model.DailyResultTypeCount;
import com.example.analyzelog.model.DateRange;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/country-detail")
public class CountryDetailController {

    private static final int TOP_LIMIT = 10;
    private final DashboardService dashboardService;

    public CountryDetailController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/result-types")
    public List<NameCount> resultTypes(
            @RequestParam String country,
            @RequestParam String from,
            @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.countryResultTypes(country, range.from(), range.to());
    }

    @GetMapping("/uri-stems")
    public List<NameCount> uriStems(
            @RequestParam String country,
            @RequestParam String from,
            @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.countryUriStems(country, range.from(), range.to(), TOP_LIMIT);
    }

    @GetMapping("/requests-per-day")
    public List<DailyResultTypeCount> requestsPerDay(
            @RequestParam String country,
            @RequestParam String from,
            @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.countryRequestsPerDay(country, range.from(), range.to());
    }
}