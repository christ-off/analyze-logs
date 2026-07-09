package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.DailyResultTypeCount;
import com.example.analyzelog.model.NameResultTypeCount;
import com.example.analyzelog.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/referer-detail")
class RefererDetailController extends DetailControllerBase {

    public RefererDetailController(DashboardService dashboardService, AppProperties appProperties) {
        super(dashboardService, appProperties);
    }

    @GetMapping("/urls")
    public List<NameResultTypeCount> urls(
            @RequestParam String referer,
            @RequestParam String from, @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = requestRange(null, from, to);
        return dashboardService.refererTopUrlsByResultType(referer, range.from(), range.to(), appProperties.topUrlsLimit(), excludeBots);
    }

    @GetMapping("/requests-per-day")
    public List<DailyResultTypeCount> requestsPerDay(
            @RequestParam String referer,
            @RequestParam String from, @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean excludeBots) {
        var range = requestRange(null, from, to);
        return dashboardService.refererRequestsPerDay(referer, range.from(), range.to(), excludeBots);
    }
}
