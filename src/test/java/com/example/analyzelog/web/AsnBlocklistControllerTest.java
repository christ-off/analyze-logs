package com.example.analyzelog.web;

import com.example.analyzelog.model.AsnBlockCandidate;
import com.example.analyzelog.service.AsnBlocklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebMvcTest(AsnBlocklistController.class)
class AsnBlocklistControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    AsnBlocklistService service;

    @Test
    void candidatesAreListed() {
        when(service.candidates(any(), any())).thenReturn(
                List.of(new AsnBlockCandidate("24940", "Hetzner", "hosting", 10, 0, true, "hosting")));

        assertThat(mvc.get().uri("/api/asn-blocklist").param("from", "2026-11-12").param("to", "2026-11-15").exchange())
                .hasStatusOk()
                .bodyJson().extractingPath("$[0].asn").isEqualTo("24940");
    }

    @Test
    void kvsExportIsADownloadableImportFile() {
        when(service.kvsImport(any())).thenReturn(Map.of("data", List.of(Map.of("key", "24940", "value", "1"))));

        assertThat(mvc.get().uri("/api/asn-blocklist/kvs.json").param("from", "2026-11-12").param("to", "2026-11-15").exchange())
                .hasStatusOk()
                .hasHeader("Content-Disposition", "attachment; filename=\"asn-blocklist.json\"")
                .bodyJson().extractingPath("$.data[0].key").isEqualTo("24940");
    }
}
