package com.example.analyzelog.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void dashboardIsPublic() {
        assertThat(mvc.get().uri("/").exchange()).hasStatusOk();
    }

    @Test
    void postRefreshWithoutCsrfTokenIsForbidden() {
        // Full @SpringBootTest context — Spring Security filter chain is active
        assertThat(mvc.post().uri("/refresh").exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void postRefreshWithInvalidCsrfTokenIsForbidden() {
        assertThat(mvc.post().uri("/refresh").with(csrf().useInvalidToken()).exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void securityHeadersArePresent() {
        assertThat(mvc.get().uri("/").exchange())
                .headers()
                .hasValue("X-Content-Type-Options", "nosniff")
                .hasValue("X-Frame-Options", "DENY");
    }
}
