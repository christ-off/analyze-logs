package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.DailyResultTypeCount;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.model.NameHumanTrafficStats;
import com.example.analyzelog.model.NameResultTypeCount;
import com.example.analyzelog.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/{browser:" + Browser.KEYS + "}")
class BrowserController extends DetailControllerBase {

    public BrowserController(DashboardService dashboardService, AppProperties appProperties) {
        super(dashboardService, appProperties);
    }

    @GetMapping("/user-agents")
    public List<NameResultTypeCount> userAgents(
            @PathVariable String browser, @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.browserRawUserAgents(label(browser), range.from(), range.to());
    }

    @GetMapping("/human-traffic")
    public List<NameHumanTrafficStats> humanTraffic(
            @PathVariable String browser, @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.browserHumanTraffic(label(browser), range.from(), range.to());
    }

    @GetMapping("/result-types")
    public List<NameCount> resultTypes(
            @PathVariable String browser, @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.browserResultTypes(label(browser), range.from(), range.to());
    }

    @GetMapping("/countries")
    public List<NameCount> countries(
            @PathVariable String browser, @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.browserCountries(label(browser), range.from(), range.to());
    }

    @GetMapping("/uri-stems")
    public List<NameResultTypeCount> uriStems(
            @PathVariable String browser, @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.browserUrlsByResultType(label(browser), range.from(), range.to(), appProperties.topUrlsLimit());
    }

    @GetMapping("/requests-per-day")
    public List<DailyResultTypeCount> requestsPerDay(
            @PathVariable String browser, @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.browserRequestsPerDay(label(browser), range.from(), range.to());
    }

    private static String label(String browserKey) {
        return Browser.fromKey(browserKey).label();
    }
}
