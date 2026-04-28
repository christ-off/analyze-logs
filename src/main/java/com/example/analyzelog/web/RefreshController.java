package com.example.analyzelog.web;

import com.example.analyzelog.service.FetchService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
public class RefreshController {

    private final FetchService fetchService;

    public RefreshController(FetchService fetchService) {
        this.fetchService = fetchService;
    }

    @PostMapping("/refresh")
    @ResponseBody
    public ResponseEntity<Map<String, String>> refresh() {
        FetchService.FetchProgress p = fetchService.getProgress();
        if (!p.done() && p.total() > 0) {
            return ResponseEntity.status(409).body(Map.of("status", "already_running"));
        }
        fetchService.startAsync(null, true);
        return ResponseEntity.status(202).body(Map.of("status", "started"));
    }

    @GetMapping("/refresh/progress")
    @ResponseBody
    public FetchService.FetchProgress progress() {
        return fetchService.getProgress();
    }
}
