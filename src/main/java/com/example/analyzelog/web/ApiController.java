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
@RequestMapping("/api")
public class ApiController {

    private final DashboardService dashboardService;
    private final AppProperties appProperties;

    public ApiController(DashboardService dashboardService, AppProperties appProperties) {
        this.dashboardService = dashboardService;
        this.appProperties = appProperties;
    }

    @GetMapping("/ua-groups")
    public List<NameCount> uaGroups(@RequestParam String from, @RequestParam String to,
                                    @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.uaGroupCounts(range.from(), range.to(), excludeBots);
    }

    @GetMapping("/ua-names-split")
    public List<NameResultTypeCount> uaNames(@RequestParam String from, @RequestParam String to,
                                             @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.topUserAgentsByResultType(range.from(), range.to(), appProperties.topLimit(), excludeBots);
    }

    @GetMapping("/countries")
    public List<CountryResultTypeCount> countries(@RequestParam String from, @RequestParam String to,
                                                  @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.topCountriesByResultType(range.from(), range.to(), appProperties.topLimit(), excludeBots);
    }

    @GetMapping("/top-urls-split")
    public List<NameResultTypeCount> topUrlsSplit(@RequestParam String from, @RequestParam String to,
                                                  @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.topUrlsByResultType(range.from(), range.to(), appProperties.topUrlsLimit(), excludeBots);
    }

    @GetMapping("/requests-per-day")
    public List<DailyResultTypeCount> requestsPerDay(@RequestParam String from, @RequestParam String to,
                                                     @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.requestsPerDay(range.from(), range.to(), excludeBots);
    }

    @GetMapping("/edge-locations")
    public List<NameCount> edgeLocations(@RequestParam String from, @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.topEdgeLocations(range.from(), range.to(), appProperties.topLimit());
    }

    @GetMapping("/referers")
    public List<NameCount> referers(@RequestParam String from, @RequestParam String to,
                                    @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.topReferers(range.from(), range.to(), appProperties.topLimit(), excludeBots);
    }
}