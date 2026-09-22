package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.NameResultTypeCount;
import com.example.analyzelog.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/zip-requests")
class ZipRequestsDetailController extends DetailControllerBase {

    public ZipRequestsDetailController(DashboardService dashboardService, AppProperties appProperties) {
        super(dashboardService, appProperties);
    }

    @GetMapping("/uris")
    public List<NameResultTypeCount> uris(@RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.zipUriCounts(range.from(), range.to(), appProperties.topUrlsLimit());
    }
}
