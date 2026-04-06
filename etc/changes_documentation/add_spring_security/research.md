# Spring Security Integration Research

## Context

This document analyses the current state of the AnalyzeLogs application and defines the integration plan for adding Spring Security with **no authentication** yet.

This migration has two goals:
1. Add the Spring Security filter chain (permissive, no auth) to gain HTTP security headers and a foundation for future auth.
2. Convert `/refresh` from GET to POST — the current GET workaround exists solely because there is no CSRF protection. Adding Spring Security removes that constraint.

---

## Current State

- **Spring Boot 4.0.5**, single Maven module
- **Thymeleaf** for server-side rendering
- **No** `spring-boot-starter-security` dependency
- **No** authentication, session management, or security configuration

### Endpoints (all GET)

| Path | Type | Writes state? |
|---|---|---|
| `/` | MVC (Thymeleaf) | No |
| `/ua-detail`, `/country-detail` | MVC (Thymeleaf) | No |
| `/api/**` (7 endpoints) | REST/JSON | No |
| `/refresh` | MVC redirect | Yes — triggers S3 fetch |

`/refresh` uses GET (not POST) only because there is no CSRF protection. This migration fixes that.

### Forms

One form exists in `dashboard.html` (date range filter). It uses `method="get"`. No changes needed there.

---

## CSRF Protection Decision

**CSRF will be enabled** as part of this migration.

Converting `/refresh` to POST introduces the first state-changing form submission. Spring Security's CSRF filter protects it by requiring a synchronizer token on every non-safe request (POST/PUT/DELETE/PATCH).

Since there is no session-based authentication, the session policy must be `IF_REQUIRED` (not `STATELESS`) so Spring Security can store and validate the CSRF token server-side across the redirect.

Thymeleaf automatically injects CSRF tokens into forms rendered with `th:action`, so no manual token management is needed in templates.

---

## What Spring Security Will Do

Adding `spring-boot-starter-security` with a permissive `SecurityFilterChain` will:

1. Permit all requests — no login required.
2. Enable **CSRF protection** for POST requests (token stored in HTTP session).
3. Add **HTTP security headers** automatically:
   - `X-Content-Type-Options: nosniff`
   - `X-Frame-Options: DENY`
   - `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`
   - `X-XSS-Protection: 0` (disabled in favour of CSP)
4. Provide a foundation for adding authentication later without structural changes.

Spring Boot will **not** generate a random password and **not** redirect to `/login` because we declare our own `SecurityFilterChain` bean.

---

## Implementation Plan

### 1. Dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.thymeleaf.extras</groupId>
    <artifactId>thymeleaf-extras-springsecurity6</artifactId>
</dependency>
```

For tests:

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

### 2. Security Configuration

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
            // Allow all requests — no authentication yet
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())

            // Session required for CSRF token storage
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

            // CSRF enabled — protects POST /refresh (and any future POST endpoints)
            // Thymeleaf + th:action injects tokens automatically

            // Disable auto-configured login/basic auth
            .formLogin(fl -> fl.disable())
            .httpBasic(hb -> hb.disable());

        return http.build();
    }
}
```

### 3. Convert `/refresh` to POST

**`RefreshController.java`** — change `@GetMapping` to `@PostMapping`:

```java
@PostMapping("/refresh")
public String refresh(RedirectAttributes redirectAttributes) {
    // existing implementation unchanged
}
```

**`dashboard.html`** — replace the refresh link with a form. Thymeleaf injects the CSRF token automatically via `th:action`:

```html
<form th:action="@{/refresh}" method="post" class="d-inline">
    <button type="submit" class="btn btn-sm btn-outline-secondary">Refresh from S3</button>
</form>
```

### 4. Tests

Existing MockMvc tests should pass unchanged. Add:

```java
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void allGetEndpointsArePublic() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/api/summary")).andExpect(status().isOk());
    }

    @Test
    void refreshRequiresPostWithCsrfToken() throws Exception {
        // GET is no longer valid
        mockMvc.perform(get("/refresh")).andExpect(status().isMethodNotAllowed());

        // POST without CSRF token is rejected
        mockMvc.perform(post("/refresh")).andExpect(status().isForbidden());

        // POST with CSRF token succeeds
        mockMvc.perform(post("/refresh").with(csrf()))
               .andExpect(status().is3xxRedirection());
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"));
    }
}
```

---

## Impact on Existing Code

| File | Change |
|---|---|
| `pom.xml` | Add `spring-boot-starter-security`, `thymeleaf-extras-springsecurity6`, `spring-security-test` |
| `SecurityConfig.java` | New file |
| `SecurityConfigTest.java` | New file |
| `RefreshController.java` | `@GetMapping` → `@PostMapping` |
| `dashboard.html` | Replace refresh anchor/link with a `<form method="post" th:action="@{/refresh}>` |
| All other controllers | None |
| All other templates | None |
| `GlobalExceptionHandler.java` | None |
| `application.yml` | None |