package com.example.analyzelog.web;

import com.example.analyzelog.service.FetchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RefreshController {

    private final FetchService fetchService;

    public RefreshController(FetchService fetchService) {
        this.fetchService = fetchService;
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh() {
        FetchService.FetchProgress p = fetchService.getProgress();
        if (!p.done() && p.total() > 0) {
            return ResponseEntity.status(409).body(Map.of("status", "already_running"));
        }
        fetchService.startAsync(null, true);
        return ResponseEntity.status(202).body(Map.of("status", "started"));
    }

    @GetMapping("/refresh/progress")
    public FetchService.FetchProgress progress() {
        return fetchService.getProgress();
    }
}
