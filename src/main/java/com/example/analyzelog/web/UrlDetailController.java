package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.CountryResultTypeCount;
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
@RequestMapping("/api/url-detail")
public class UrlDetailController {

    private final DashboardService dashboardService;
    private final AppProperties appProperties;

    public UrlDetailController(DashboardService dashboardService, AppProperties appProperties) {
        this.dashboardService = dashboardService;
        this.appProperties = appProperties;
    }

    @GetMapping("/urls")
    public List<NameCount> urls(
            @RequestParam String url,
            @RequestParam String from,
            @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.urlMatchingUriStems(url, range.from(), range.to());
    }

    @GetMapping("/countries")
    public List<CountryResultTypeCount> countries(
            @RequestParam String url,
            @RequestParam String from,
            @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.urlTopCountriesByResultType(url, range.from(), range.to(), appProperties.topLimit());
    }

    @GetMapping("/user-agents")
    public List<NameResultTypeCount> userAgents(
            @RequestParam String url,
            @RequestParam String from,
            @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.urlTopUserAgentsByResultType(url, range.from(), range.to(), appProperties.topLimit());
    }

    @GetMapping("/requests-per-day")
    public List<DailyResultTypeCount> requestsPerDay(
            @RequestParam String url,
            @RequestParam String from,
            @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.urlRequestsPerDay(url, range.from(), range.to());
    }
}