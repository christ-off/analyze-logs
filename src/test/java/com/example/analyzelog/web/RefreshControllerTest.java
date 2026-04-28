package com.example.analyzelog.web;

import com.example.analyzelog.service.FetchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(RefreshController.class)
class RefreshControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    FetchService fetchService;

    static FetchService.FetchProgress idle() {
        return new FetchService.FetchProgress(0, 0, 0, 0, 0, false, null);
    }

    static FetchService.FetchProgress running() {
        return new FetchService.FetchProgress(10, 3, 2, 1, 0, false, null);
    }

    @Test
    void getProgressReturnsOk() {
        when(fetchService.getProgress()).thenReturn(idle());

        assertThat(mvc.get().uri("/refresh/progress").exchange())
                .hasStatus(HttpStatus.OK)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
    }

    @Test
    void postRefreshAcceptedWhenIdle() {
        when(fetchService.getProgress()).thenReturn(idle());

        assertThat(mvc.post().uri("/refresh").with(csrf()).exchange())
                .hasStatus(HttpStatus.ACCEPTED);

        verify(fetchService).startAsync(isNull(), anyBoolean());
    }

    @Test
    void postRefreshConflictWhenAlreadyRunning() {
        when(fetchService.getProgress()).thenReturn(running());

        assertThat(mvc.post().uri("/refresh").with(csrf()).exchange())
                .hasStatus(HttpStatus.CONFLICT);

        verify(fetchService, never()).startAsync(any(), anyBoolean());
    }

}
