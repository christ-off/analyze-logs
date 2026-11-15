package com.example.analyzelog.web;

import com.example.analyzelog.model.AsnBlockCandidate;
import com.example.analyzelog.model.DateRange;
import com.example.analyzelog.service.AsnBlocklistService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/asn-blocklist")
public class AsnBlocklistController {

    private final AsnBlocklistService service;

    public AsnBlocklistController(AsnBlocklistService service) {
        this.service = service;
    }

    @GetMapping
    public List<AsnBlockCandidate> candidates(@RequestParam String from, @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return service.candidates(range.from(), range.to());
    }

    @GetMapping("/kvs.json")
    public ResponseEntity<Map<String, Object>> kvsImport(@RequestParam String from, @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("asn-blocklist.json").build().toString())
                .body(service.kvsImport(service.candidates(range.from(), range.to())));
    }
}
