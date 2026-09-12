package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.DailyResultTypeCount;
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
class CountryDetailController extends DetailControllerBase {

    public CountryDetailController(DashboardService dashboardService, AppProperties appProperties) {
        super(dashboardService, appProperties);
    }

    @GetMapping("/ua-split")
    public List<NameResultTypeCount> uaSplit(
            @RequestParam String country,
            @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.countryTopUserAgentsByResultType(country, range.from(), range.to(), appProperties.topLimit());
    }

    @GetMapping("/traffic-categories")
    public List<NameResultTypeCount> trafficCategories(
            @RequestParam String country,
            @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.trafficCategories(country, range.from(), range.to());
    }

    @GetMapping("/result-types")
    public List<NameCount> resultTypes(
            @RequestParam String country,
            @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.countryResultTypes(country, range.from(), range.to());
    }

    @GetMapping("/url-split")
    public List<NameResultTypeCount> urlSplit(
            @RequestParam String country,
            @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.countryUrlsByResultType(country, range.from(), range.to(), appProperties.topUrlsLimit());
    }

    @GetMapping("/requests-per-day")
    public List<DailyResultTypeCount> requestsPerDay(
            @RequestParam String country,
            @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.countryRequestsPerDay(country, range.from(), range.to());
    }
}
