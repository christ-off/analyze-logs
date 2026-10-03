package com.example.analyzelog.web;

import com.example.analyzelog.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class IpRequestsController extends DateRangeController {

    private final DashboardService dashboardService;

    public IpRequestsController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/ip-requests")
    public String ipRequests(
            @RequestParam String ip,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Model model) {
        var dateRange = resolveRange(range, from, to);
        addDateAttributes(model, dateRange, resolveActiveRange(range, from, to));
        model.addAttribute("ip", ip);
        model.addAttribute("requests", dashboardService.requestsByIp(ip, dateRange.from(), dateRange.to()));
        return "ip-requests";
    }
}
