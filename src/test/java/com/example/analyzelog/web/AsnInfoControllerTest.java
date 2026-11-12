package com.example.analyzelog.web;

import com.example.analyzelog.service.AsnInfoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(AsnInfoController.class)
class AsnInfoControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    AsnInfoService asnInfoService;

    @Test
    void refreshReturnsTheNumberOfLoadedNames() {
        when(asnInfoService.refresh()).thenReturn(120000);

        assertThat(mvc.post().uri("/asn/names/refresh").with(csrf()).exchange())
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.count").isEqualTo(120000);
    }

    @Test
    void refreshReturnsBadGatewayWhenTheDownloadFails() {
        when(asnInfoService.refresh()).thenThrow(new IllegalStateException("boom"));

        assertThat(mvc.post().uri("/asn/names/refresh").with(csrf()).exchange())
                .hasStatus(HttpStatus.BAD_GATEWAY)
                .bodyJson().extractingPath("$.error").asString().contains("boom");
    }
}
