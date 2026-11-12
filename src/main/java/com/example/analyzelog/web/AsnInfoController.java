package com.example.analyzelog.web;

import com.example.analyzelog.service.AsnInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AsnInfoController {

    private static final Logger log = LoggerFactory.getLogger(AsnInfoController.class);

    private final AsnInfoService asnInfoService;

    public AsnInfoController(AsnInfoService asnInfoService) {
        this.asnInfoService = asnInfoService;
    }

    @PostMapping("/asn/names/refresh")
    public ResponseEntity<Map<String, Object>> refresh() {
        try {
            return ResponseEntity.ok(Map.of("count", asnInfoService.refresh()));
        } catch (RuntimeException e) {
            log.error("ASN names download failed: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of("error", "Download failed: " + e.getMessage()));
        }
    }
}
