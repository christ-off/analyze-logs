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
@RequestMapping("/api/asn-detail")
class AsnDetailController extends DetailControllerBase {

    public AsnDetailController(DashboardService dashboardService, AppProperties appProperties) {
        super(dashboardService, appProperties);
    }

    @GetMapping("/ua-split")
    public List<NameResultTypeCount> uaSplit(
            @RequestParam String asn,
            @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.asnTopUserAgentsByResultType(asn, range.from(), range.to(), appProperties.topLimit());
    }

    @GetMapping("/url-split")
    public List<NameResultTypeCount> urlSplit(
            @RequestParam String asn,
            @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.asnUrlsByResultType(asn, range.from(), range.to(), appProperties.topUrlsLimit());
    }

    @GetMapping("/requests-per-day")
    public List<DailyResultTypeCount> requestsPerDay(
            @RequestParam String asn,
            @RequestParam String from, @RequestParam String to) {
        var range = range(from, to);
        return dashboardService.asnRequestsPerDay(asn, range.from(), range.to());
    }
}
