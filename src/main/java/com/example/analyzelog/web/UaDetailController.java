package com.example.analyzelog.web;

import com.example.analyzelog.model.DateRange;
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
@RequestMapping("/api/ua-detail")
public class UaDetailController {

    private final DashboardService dashboardService;

    public UaDetailController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/user-agents")
    public List<NameResultTypeCount> userAgents(
            @RequestParam String ua,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.uaRawUserAgents(ua, range.from(), range.to(), excludeBots);
    }

    @GetMapping("/result-types")
    public List<NameCount> resultTypes(
            @RequestParam String ua,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.uaResultTypes(ua, range.from(), range.to(), excludeBots);
    }

    @GetMapping("/countries")
    public List<NameCount> countries(
            @RequestParam String ua,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.uaCountries(ua, range.from(), range.to(), excludeBots);
    }

    @GetMapping("/uri-stems")
    public List<NameResultTypeCount> uriStems(
            @RequestParam String ua,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.uaUrlsByResultType(ua, range.from(), range.to(), ApiConstants.TOP_URLS_LIMIT, excludeBots);
    }

    @GetMapping("/requests-per-day")
    public List<DailyResultTypeCount> requestsPerDay(
            @RequestParam String ua,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.uaRequestsPerDay(ua, range.from(), range.to(), excludeBots);
    }
}
