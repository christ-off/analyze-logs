package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.DailyFeedSubscribers;
import com.example.analyzelog.model.NameResultTypeCount;
import com.example.analyzelog.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/feed-readers")
class FeedReadersDetailController extends DetailControllerBase {

    public FeedReadersDetailController(DashboardService dashboardService, AppProperties appProperties) {
        super(dashboardService, appProperties);
    }

    @GetMapping("/user-agents")
    public List<NameResultTypeCount> userAgents(@RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.feedReaderUserAgents(range.from(), range.to(), appProperties.topDetailLimit());
    }

    @GetMapping("/subscribers-per-day")
    public List<DailyFeedSubscribers> subscribersPerDay(@RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.feedSubscribersPerDay(range.from(), range.to());
    }
}
