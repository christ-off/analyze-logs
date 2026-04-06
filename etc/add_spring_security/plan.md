# Migration Plan — Add Spring Security

Based on [research.md](research.md).

## Scope

- Add `spring-boot-starter-security` with a permissive filter chain (no authentication).
- Enable CSRF protection.
- Convert `GET /refresh` → `POST /refresh` (the GET workaround becomes obsolete).
- Add/update tests.

## Files changed

| File | Action |
|---|---|
| `pom.xml` | Add 3 dependencies |
| `src/main/java/.../config/SecurityConfig.java` | Create |
| `src/main/java/.../web/RefreshController.java` | `@GetMapping` → `@PostMapping` |
| `src/main/resources/templates/dashboard.html` | Replace refresh `<a>` with POST `<form>` |
| `src/test/java/.../web/RefreshControllerTest.java` | Create |
| `src/test/java/.../web/SecurityConfigTest.java` | Create |

---

## Step 1 — `pom.xml`: add dependencies ✅

Add inside `<dependencies>`, after the existing Thymeleaf dependency:

```xml
<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<!-- Thymeleaf Spring Security dialect (auto-injects CSRF tokens in th:action forms) -->
<dependency>
    <groupId>org.thymeleaf.extras</groupId>
    <artifactId>thymeleaf-extras-springsecurity6</artifactId>
</dependency>

<!-- Test -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Step 2 — Create `SecurityConfig.java` ✅

New file: `src/main/java/com/example/analyzelog/config/SecurityConfig.java`

```java
package com.example.analyzelog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // No authentication — all requests permitted
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())

            // Session required for CSRF token storage across the POST → redirect
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

            // CSRF enabled (default) — protects POST /refresh
            // Thymeleaf injects tokens automatically via th:action

            // No form login or HTTP Basic
            .formLogin(fl -> fl.disable())
            .httpBasic(hb -> hb.disable());

        return http.build();
    }
}
```

---

## Step 3 — `RefreshController.java`: GET → POST ✅

File: `src/main/java/com/example/analyzelog/web/RefreshController.java`

- Line 5: replace `import org.springframework.web.bind.annotation.GetMapping;`
  with `import org.springframework.web.bind.annotation.PostMapping;`
- Line 17: replace `@GetMapping("/refresh")`
  with `@PostMapping("/refresh")`

Result:

```java
@PostMapping("/refresh")
public String refresh(RedirectAttributes redirectAttributes) {
    var result = fetchService.fetch(null, true);
    redirectAttributes.addFlashAttribute("flashMessage",
        "Refresh complete — fetched: %d, skipped: %d, failed: %d"
            .formatted(result.fetched(), result.skipped(), result.failed()));
    return "redirect:/";
}
```

---

## Step 4 — `dashboard.html`: replace refresh link with POST form ✅

File: `src/main/resources/templates/dashboard.html`

Line 41 — current:
```html
<a th:href="@{/refresh}" class="ms-auto btn btn-sm btn-warning">Refresh from S3</a>
```

Replace with (Thymeleaf injects the CSRF token automatically because of `th:action`):
```html
<form th:action="@{/refresh}" method="post" class="ms-auto d-inline">
    <button type="submit" class="btn btn-sm btn-warning">Refresh from S3</button>
</form>
```

Also add the Spring Security Thymeleaf namespace to the `<html>` tag (line 2) so the dialect is active:
```html
<html lang="en" xmlns:th="http://www.thymeleaf.org"
                xmlns:sec="http://www.thymeleaf.org/extras/spring-security">
```

---

## Step 5 — Create `RefreshControllerTest.java` ✅

New file: `src/test/java/com/example/analyzelog/web/RefreshControllerTest.java`

```java
package com.example.analyzelog.web;

import com.example.analyzelog.service.FetchService;
import com.example.analyzelog.service.FetchResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
    void postWithoutCsrfTokenIsForbidden() {
        assertThat(mvc.post().uri("/refresh").exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void postWithCsrfTokenRedirectsToDashboard() {
        when(fetchService.fetch(any(), anyBoolean()))
                .thenReturn(new FetchResult(3, 1, 0));

        assertThat(mvc.post().uri("/refresh").with(csrf()).exchange())
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/");
    }
}
```

> **Note:** `FetchResult` record name — verify against the actual return type of `FetchService.fetch()` before writing the test.

---

## Step 6 — Create `SecurityConfigTest.java` ✅

New file: `src/test/java/com/example/analyzelog/web/SecurityConfigTest.java`

```java
package com.example.analyzelog.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

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
    void apiEndpointsArePublic() {
        assertThat(mvc.get().uri("/api/summary").exchange()).hasStatusOk();
    }

    @Test
    void securityHeadersArePresent() {
        assertThat(mvc.get().uri("/").exchange())
                .headers()
                .containsEntry("X-Content-Type-Options", java.util.List.of("nosniff"))
                .containsEntry("X-Frame-Options", java.util.List.of("DENY"));
    }
}
```

---

## Impact on existing tests

`@WebMvcTest` slices load `SecurityConfig` (it is a `@Configuration @EnableWebSecurity` in the web layer). Since `SecurityConfig` uses `permitAll()`, all existing GET-based tests in `DashboardControllerTest`, `ApiControllerTest`, and `UaDetailControllerTest` continue to pass without changes.

**Spring Boot 4 note:** In Spring Boot 4's `@WebMvcTest`, `SecurityFilterAutoConfiguration` is NOT part of the test slice, so the Spring Security filter chain is not applied to MockMvc requests. CSRF enforcement cannot be tested in `@WebMvcTest`. CSRF tests live in `SecurityConfigTest` which uses `@SpringBootTest` where the full filter chain is active.

---

## Verification checklist

- [x] `mvn test` passes with no failures (132 tests)
- [x] `GET /refresh` returns 405 Method Not Allowed
- [x] `POST /refresh` without token returns 403 Forbidden (verified in `SecurityConfigTest`)
- [x] `POST /refresh` with invalid token returns 403 Forbidden (verified in `SecurityConfigTest`)
- [x] `POST /refresh` with valid CSRF token redirects to `/` (verified in `RefreshControllerTest`)
- [x] `X-Content-Type-Options: nosniff` and `X-Frame-Options: DENY` headers present (verified in `SecurityConfigTest`)
- [x] No login prompt is shown on any page (`permitAll()` in `SecurityConfig`)