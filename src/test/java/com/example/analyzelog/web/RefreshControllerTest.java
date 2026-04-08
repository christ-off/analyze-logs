package com.example.analyzelog.web;

import com.example.analyzelog.service.FetchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(RefreshController.class)
class RefreshControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    FetchService fetchService;

    @Test
    void getRefreshIsNotAllowed() {
        assertThat(mvc.get().uri("/refresh").exchange())
                .hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void postRedirectsToDashboard() {
        when(fetchService.fetch(isNull(), anyBoolean()))
                .thenReturn(new FetchService.FetchResult(3, 1, 0));

        assertThat(mvc.post().uri("/refresh").with(csrf()).exchange())
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/");
    }

    @Test
    void postErrorRedirectsHomeWithFlash() {
        when(fetchService.fetch(isNull(), anyBoolean()))
                .thenThrow(new RuntimeException("S3 unavailable"));

        assertThat(mvc.post().uri("/refresh").with(csrf()).exchange())
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/");
    }
}
