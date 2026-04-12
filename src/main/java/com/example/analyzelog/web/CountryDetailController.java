package com.example.analyzelog.web;

import com.example.analyzelog.model.DailyResultTypeCount;
import com.example.analyzelog.model.DateRange;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.model.NameResultTypeCount;
import com.example.analyzelog.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/country-detail")
public class CountryDetailController {

    private static final int TOP_URLS_LIMIT = 20;
    private static final int TOP_UA_LIMIT   = 10;
    private final DashboardService dashboardService;

    public CountryDetailController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/ua-split")
    public List<NameResultTypeCount> uaSplit(
            @RequestParam String country,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.countryTopUserAgentsByResultType(country, range.from(), range.to(), TOP_UA_LIMIT, excludeBots);
    }

    @GetMapping("/result-types")
    public List<NameCount> resultTypes(
            @RequestParam String country,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.countryResultTypes(country, range.from(), range.to(), excludeBots);
    }

    @GetMapping("/url-split")
    public List<NameResultTypeCount> urlSplit(
            @RequestParam String country,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.countryUrlsByResultType(country, range.from(), range.to(), TOP_URLS_LIMIT, excludeBots);
    }

    @GetMapping("/requests-per-day")
    public List<DailyResultTypeCount> requestsPerDay(
            @RequestParam String country,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.countryRequestsPerDay(country, range.from(), range.to(), excludeBots);
    }
}
