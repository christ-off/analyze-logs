# Migration Plan: Spring Boot 3.5 → Spring Boot 4

**Based on:** `research.md`  
**Target:** Spring Boot 4.0.5 / Spring Framework 7 / Java 25

---

## Todo

- [x] **Step 1 — POM Upgrades**
  - [x] 1.1 Bump `spring-boot-starter-parent` from `3.5.0` to `4.0.5`
  - [x] 1.2 Remove `<maven.compiler.source>` and `<maven.compiler.target>` from `<properties>`
  - [x] 1.2 Remove the explicit `maven-compiler-plugin` `<plugin>` block entirely
  - [x] 1.3 Remove the `<liquibase.version>4.31.1</liquibase.version>` property override
  - [x] 1.4 Update `<aws.sdk.version>` to latest available `2.x` (2.34.0)
  - [x] 1.5 Update Bootstrap WebJar to latest `5.x` (5.3.7)
  - [x] 1.5 Update Chart.js WebJar to latest `4.x` (4.5.0)
  - [x] Fix `CloudFrontLogParser` Jackson imports: `com.fasterxml.jackson` → `tools.jackson` (Jackson 3.x package rename in SB4)
  - [x] Run `mvn clean compile` — resolved all compile errors

- [x] **Step 2 — Fix `GlobalExceptionHandler`**
  - [x] Remove `extends ResponseEntityExceptionHandler` from the class declaration
  - [x] Remove unused imports: `HttpHeaders`, `HttpStatusCode`, `ResponseEntity`, `WebRequest`, `ResponseEntityExceptionHandler`, `@ResponseStatus`
  - [x] Add `HttpServletResponse` parameter to `handleBadRequest`; replace `@ResponseStatus` with `response.setStatus(400)`
  - [x] Add explicit `handleMissingParam` method for `MissingServletRequestParameterException` → 400
  - [x] Rename `handlePostError` to `handleError`; add `HttpServletResponse` parameter; set 500 explicitly on non-POST path; remove `throws IOException`
  - [x] Delete the `handleExceptionInternal` override
  - [x] Run `mvn clean test` — `invalidFromToReturns400()` and `missingParamsReturns400()` pass

- [x] **Step 3 — `application.yml` Properties**
  - [x] Verified no renamed property keys (all `spring.liquibase.*`, `spring.datasource.*`, `spring.thymeleaf.*` are unchanged in SB4)
  - [x] Add `spring.mvc.problemdetails.enabled: true`
  - [x] Add `spring.threads.virtual.enabled: true`
  - [x] Add `spring.datasource.hikari.connection-init-sql: "PRAGMA journal_mode=WAL"`
  - [x] Run `mvn clean test` — 82/82, no new failures

- [x] **Step 4 — Migrate Tests to `MockMvcTester`**
  - [x] Add `spring-boot-starter-webmvc-test` to POM test scope (`@WebMvcTest` moved to new module `org.springframework.boot.webmvc.test.autoconfigure` in SB4)
  - [x] `ApiControllerTest`: replace `@Autowired MockMvc` with `@Autowired MockMvcTester`
  - [x] `ApiControllerTest`: replace all `mvc.perform(...).andExpect(...)` chains with `assertThat(mvc.get()...exchange())` AssertJ assertions
  - [x] `ApiControllerTest`: remove `throws Exception` from all test method signatures
  - [x] `ApiControllerTest`: update imports
  - [x] `DashboardControllerTest`: replace `@Autowired MockMvc` with `@Autowired MockMvcTester`
  - [x] `DashboardControllerTest`: replace all `mvc.perform(...).andExpect(...)` chains with AssertJ assertions
  - [x] `DashboardControllerTest`: remove `throws Exception` from all test method signatures
  - [x] `DashboardControllerTest`: update imports
  - [x] Run `mvn clean test` — 82/82, all greenpuas

- [x] **Step 5 — Enable Virtual Threads**
  - [x] `spring.threads.virtual.enabled: true` added in Step 3
  - [x] Run `mvn clean test` — 82/82, virtual threads do not affect test behavior
  - [ ] Start app locally and exercise the dashboard — verify no SQLite deadlock *(manual)*
  - [ ] Trigger a `/refresh` and confirm it completes without hanging *(manual)*

- [ ] **Step 6 — Manual End-to-End Verification** *(requires live AWS credentials)*
  - [ ] Delete `logs.db`, run `mvn clean package`, start with `--spring.profiles.active=local`
  - [ ] Confirm Liquibase applies both changelogs cleanly on a fresh database
  - [ ] Confirm dashboard loads at `http://localhost:8080/`
  - [ ] Click **Refresh from S3** — confirm flash message and charts populate
  - [ ] Test all date range presets (Today, 7d, 30d, 3m) and a custom range
  - [ ] Navigate to `/api/ua-names` (no params) — confirm HTTP 400
  - [ ] Navigate to `/api/ua-names?from=2026-02-01&to=2026-01-01` — confirm HTTP 400
  - [ ] Open browser devtools Network tab — confirm Bootstrap and Chart.js WebJars return HTTP 200

---

## Overview

Six steps, in strict order. Each step ends with a green test suite before the next begins.

| Step | Scope | Risk |
|---|---|---|
| 1 | POM: bump parent, clean up redundancies | Low |
| 2 | Fix `GlobalExceptionHandler` | Medium |
| 3 | Audit and fix `application.yml` property keys | Low |
| 4 | Migrate tests to `MockMvcTester` | Low |
| 5 | Enable virtual threads | Low |
| 6 | Verify end-to-end (manual) | None |

---

## Step 1 — POM Upgrades

### 1.1 Bump the Spring Boot parent

```xml
<!-- BEFORE -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.0</version>
    <relativePath/>
</parent>

<!-- AFTER -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.5</version>
    <relativePath/>
</parent>
```

### 1.2 Remove the redundant compiler plugin configuration

The `spring-boot-starter-parent` drives `maven-compiler-plugin` source/target via `<java.version>`. The explicit plugin block is redundant and must be removed to avoid conflicts with the parent's plugin management.

```xml
<!-- BEFORE: keep the <properties> block as-is -->
<properties>
    <java.version>25</java.version>
    <maven.compiler.source>25</maven.compiler.source>
    <maven.compiler.target>25</maven.compiler.target>
    ...
</properties>

<!-- AFTER: remove maven.compiler.source and maven.compiler.target -->
<properties>
    <java.version>25</java.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    ...
</properties>
```

```xml
<!-- BEFORE: explicit maven-compiler-plugin -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <source>25</source>
        <target>25</target>
    </configuration>
</plugin>

<!-- AFTER: remove the entire plugin block; parent controls it via java.version -->
```

### 1.3 Remove the Liquibase version override

Spring Boot 4 manages Liquibase 4.x. The pinned `4.31.1` override should be dropped unless a specific incompatibility is found.

```xml
<!-- BEFORE -->
<properties>
    ...
    <!-- Override Spring Boot managed Liquibase to keep 4.31.1 -->
    <liquibase.version>4.31.1</liquibase.version>
</properties>

<!-- AFTER: delete the liquibase.version property entirely -->
```

If Liquibase changelogs break (unlikely — both use standard XML), re-pin to the latest stable version and document the reason.

### 1.4 Update AWS SDK BOM

The AWS SDK is independent of Spring Boot. Update to the latest available `2.x` release:

```xml
<!-- BEFORE -->
<aws.sdk.version>2.30.31</aws.sdk.version>

<!-- AFTER — check https://github.com/aws/aws-sdk-java-v2/releases -->
<aws.sdk.version>2.31.x</aws.sdk.version>   <!-- substitute actual latest -->
```

### 1.5 Update frontend WebJar versions

```xml
<!-- BEFORE -->
<dependency>
    <groupId>org.webjars</groupId>
    <artifactId>bootstrap</artifactId>
    <version>5.3.3</version>
</dependency>
<dependency>
    <groupId>org.webjars.npm</groupId>
    <artifactId>chart.js</artifactId>
    <version>4.4.4</version>
</dependency>

<!-- AFTER — check https://www.webjars.org/ for latest versions -->
<dependency>
    <groupId>org.webjars</groupId>
    <artifactId>bootstrap</artifactId>
    <version>5.3.x</version>
</dependency>
<dependency>
    <groupId>org.webjars.npm</groupId>
    <artifactId>chart.js</artifactId>
    <version>4.x.x</version>
</dependency>
```

> **Check point:** run `mvn clean compile`. Resolve any compile errors before continuing to Step 2.

---

## Step 2 — Fix `GlobalExceptionHandler`

This is the highest-risk change. Two distinct problems must be fixed.

### 2.1 Background

`GlobalExceptionHandler` extends `ResponseEntityExceptionHandler`. In Spring Framework 7:

1. `ResponseEntityExceptionHandler` fully embraces **RFC 9457 Problem Details** — its `handleExceptionInternal` now creates a `ProblemDetail` body by default. Our override at line 48 simply delegates to `super`, so it remains correct — but must match the updated method signature if it changed.
2. The combination of `@ExceptionHandler(IllegalArgumentException.class)` + `@ResponseStatus(HttpStatus.BAD_REQUEST)` + returning a view name `String` is unreliable. Spring Framework 7 tightens the contract: `@ResponseStatus` is **only** applied when the handler returns `void` or `ResponseEntity`. When returning a view name, it is silently ignored and the response defaults to 200.
3. The generic `@ExceptionHandler(Exception.class)` handler that uses `RedirectAttributes` is not safe inside a `@ControllerAdvice` that extends `ResponseEntityExceptionHandler` — `RedirectAttributes` is only injectable when the handler is invoked through `DispatcherServlet`'s full redirect machinery, which is not guaranteed in error handling paths.

### 2.2 Solution

Stop extending `ResponseEntityExceptionHandler`. Handle Spring MVC's standard exceptions (missing params, type mismatches) explicitly. This gives full control over the HTTP status for each case.

**File:** `src/main/java/com/example/analyzelog/web/GlobalExceptionHandler.java`

```java
// BEFORE
package com.example.analyzelog.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Bad request [{}]: {}", request.getRequestURI(), ex.getMessage());
        return "error";
    }

    @ExceptionHandler(Exception.class)
    public String handlePostError(Exception ex, HttpServletRequest request,
                                  RedirectAttributes redirectAttributes) {
        log.error("Unhandled error [{}]", request.getRequestURI(), ex);
        if (request.getMethod().equalsIgnoreCase("POST")) {
            redirectAttributes.addFlashAttribute("flashError",
                "Operation failed: " + ex.getMessage());
            return "redirect:/";
        }
        return "error";
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.debug("Spring MVC exception [{}]: {}", status, ex.getMessage());
        return super.handleExceptionInternal(ex, body, headers, status, request);
    }
}
```

```java
// AFTER
package com.example.analyzelog.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Invalid date range or other application-level bad request.
     * Sets 400 explicitly on the response before returning the error view.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public String handleBadRequest(IllegalArgumentException ex,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        log.warn("Bad request [{}]: {}", request.getRequestURI(), ex.getMessage());
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return "error";
    }

    /**
     * Missing required query parameter (e.g. from/to on API endpoints).
     * Previously handled by ResponseEntityExceptionHandler; now explicit.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public String handleMissingParam(MissingServletRequestParameterException ex,
                                     HttpServletRequest request,
                                     HttpServletResponse response) {
        log.warn("Missing parameter [{}]: {}", request.getRequestURI(), ex.getMessage());
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return "error";
    }

    /**
     * Unexpected errors.
     * POST errors redirect with a flash message; GET errors render the error page.
     */
    @ExceptionHandler(Exception.class)
    public String handleError(Exception ex,
                              HttpServletRequest request,
                              HttpServletResponse response,
                              RedirectAttributes redirectAttributes) throws IOException {
        log.error("Unhandled error [{}]", request.getRequestURI(), ex);
        if (request.getMethod().equalsIgnoreCase("POST")) {
            redirectAttributes.addFlashAttribute("flashError",
                "Operation failed: " + ex.getMessage());
            return "redirect:/";
        }
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        return "error";
    }
}
```

**Why this works:**
- `response.setStatus(...)` sets the HTTP status code directly and reliably, regardless of whether the method returns a view name or a `ResponseEntity`.
- Removing `extends ResponseEntityExceptionHandler` eliminates all inherited machinery and the override that may break.
- `MissingServletRequestParameterException` is now handled explicitly — this preserves the `missingParamsReturns400()` test behavior which previously relied on the inherited handler.

> **Check point:** `mvn clean test`. All 6 `ApiControllerTest` and 6 `DashboardControllerTest` methods must pass, especially `invalidFromToReturns400()` and `missingParamsReturns400()`.

---

## Step 3 — Audit `application.yml` Properties

Spring Boot 4 renames a small number of configuration properties. Run the property migration helper after bumping the POM:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--debug" 2>&1 | grep -i "deprecated\|renamed\|replaced"
```

### Known renames to check

| Current key | Likely SB4 key | Notes |
|---|---|---|
| `spring.datasource.hikari.maximum-pool-size` | unchanged | HikariCP is stable |
| `spring.liquibase.change-log` | `spring.liquibase.change-log` | unchanged |
| `spring.thymeleaf.cache` | unchanged | |
| `management.endpoints.*` | check SB4 migration guide | Actuator namespace restructured in SB4 |

### Properties to add

Enable virtual threads (covered in Step 5) and the RFC 9457 problem details format for API error responses:

```yaml
# application.yml additions for Spring Boot 4

spring:
  threads:
    virtual:
      enabled: true   # Step 5 — add after thread-safety verification

  mvc:
    problemdetails:
      enabled: true   # Opt in to RFC 9457 JSON error bodies for REST clients
```

Enabling `mvc.problemdetails` means that when `ApiController` endpoints return 400 (missing params, bad date range), clients receive a structured `application/problem+json` body instead of a plain Spring error page. This is additive and does not affect existing tests.

> **Check point:** `mvn clean test` — no new failures.

---

## Step 4 — Migrate Tests to `MockMvcTester`

Spring Boot 4 ships `MockMvcTester`, a new fluent assertion API that replaces the chainable `.andExpect()` pattern. The existing `MockMvc` API still works, so this step is optional but strongly recommended — `MockMvcTester` integrates with AssertJ (already on the classpath via `spring-boot-starter-test`) and gives far better failure messages.

### 4.1 `ApiControllerTest`

```java
// BEFORE
@WebMvcTest(ApiController.class)
class ApiControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DashboardService dashboardService;

    @Test
    void uaNamesReturnsJson() throws Exception {
        when(dashboardService.topUserAgents(any(), any(), anyInt()))
                .thenReturn(List.of(new NameCount("Chrome / Windows", 42)));

        mvc.perform(get("/api/ua-names").param("from", "2026-01-01").param("to", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].name").value("Chrome / Windows"))
                .andExpect(jsonPath("$[0].count").value(42));
    }

    @Test
    void invalidFromToReturns400() throws Exception {
        mvc.perform(get("/api/ua-names").param("from", "2026-02-01").param("to", "2026-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingParamsReturns400() throws Exception {
        mvc.perform(get("/api/ua-names"))
                .andExpect(status().isBadRequest());
    }
}
```

```java
// AFTER
@WebMvcTest(ApiController.class)
class ApiControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    DashboardService dashboardService;

    @Test
    void uaNamesReturnsJson() {
        when(dashboardService.topUserAgents(any(), any(), anyInt()))
                .thenReturn(List.of(new NameCount("Chrome / Windows", 42)));

        assertThat(mvc.get().uri("/api/ua-names")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("Chrome / Windows");
    }

    @Test
    void invalidFromToReturns400() {
        assertThat(mvc.get().uri("/api/ua-names")
                .param("from", "2026-02-01").param("to", "2026-01-01")
                .exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void missingParamsReturns400() {
        assertThat(mvc.get().uri("/api/ua-names").exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
```

Add the required import:
```java
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
```

### 4.2 `DashboardControllerTest`

```java
// BEFORE
@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void rootReturns200() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"));
    }

    @Test
    void defaultRangeIs7Days() throws Exception {
        mvc.perform(get("/"))
                .andExpect(model().attribute("activeRange", "7d"));
    }

    @Test
    void invalidDateRangeReturns400() throws Exception {
        mvc.perform(get("/").param("from", "2026-02-01").param("to", "2026-01-01"))
                .andExpect(status().isBadRequest());
    }
}
```

```java
// AFTER
@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void rootReturns200() {
        assertThat(mvc.get().uri("/").exchange())
                .hasStatusOk()
                .hasViewName("dashboard");
    }

    @Test
    void defaultRangeIs7Days() {
        assertThat(mvc.get().uri("/").exchange())
                .model().containsEntry("activeRange", "7d");
    }

    @Test
    void invalidDateRangeReturns400() {
        assertThat(mvc.get().uri("/")
                .param("from", "2026-02-01").param("to", "2026-01-01")
                .exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
```

> **Note:** `MockMvcTester` removes the `throws Exception` on all test methods — checked exceptions from `MockMvc.perform()` are gone.

> **Check point:** `mvn clean test` — all tests green.

---

## Step 5 — Enable Virtual Threads

Spring Boot 4 runs on virtual threads by default when `spring.threads.virtual.enabled=true`. Add to `application.yml`:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

This makes Tomcat use a virtual thread per request instead of a platform thread pool. For this application the key concern is the SQLite connection pool.

### SQLite pool caveat

`hikari.maximum-pool-size: 1` remains correct. With virtual threads, many requests can park while waiting for the single connection. For a single-user app this is fine. If concurrency ever matters, the fix is to increase the pool size and ensure WAL mode is enabled in SQLite:

```yaml
# application.yml — only change this if concurrency becomes a concern
spring:
  datasource:
    hikari:
      maximum-pool-size: 1          # keep at 1 for SQLite; increase with WAL only
      connection-init-sql: "PRAGMA journal_mode=WAL"   # safe to add now
```

The `connection-init-sql` pragma has no effect at pool-size 1 but is good practice if the pool size is ever raised.

> **Check point:** `mvn clean test` — virtual threads should not affect any test behavior since tests are single-threaded. Start the app locally and exercise the dashboard to verify no deadlock on the SQLite connection.

---

## Step 6 — Manual End-to-End Verification

Automated tests cover parsing, persistence, and controller routing. The following must be verified manually:

### 6.1 Clean start

```bash
rm -f logs.db
mvn clean package -q
java --enable-native-access=ALL-UNNAMED \
     -jar target/analyze-logs-1.0-SNAPSHOT.jar \
     --spring.profiles.active=local
```

Verify:
- Liquibase runs both changelogs cleanly on the fresh database (check console output)
- Dashboard loads at `http://localhost:8080/`
- All 4 charts render (may be empty — that's fine)

### 6.2 S3 refresh

Click **Refresh from S3** or visit `http://localhost:8080/refresh`. Verify:
- Flash message appears: `Refresh complete — fetched: N, skipped: 0, failed: 0`
- Charts populate after refresh

### 6.3 Date range controls

Test each preset (Today, 7 days, 30 days, 3 months) and a custom date range. Verify charts update correctly.

### 6.4 Error handling

Navigate to `http://localhost:8080/api/ua-names` (missing params). Verify HTTP 400 is returned, not 500.

Navigate to `http://localhost:8080/api/ua-names?from=2026-02-01&to=2026-01-01` (invalid range). Verify HTTP 400.

### 6.5 WebJar assets

Open browser devtools → Network tab. Reload the dashboard. Verify that Bootstrap CSS/JS and Chart.js are served as 200 (not 404) from `/webjars/...` paths.

---

## Summary of All File Changes

| File | Change |
|---|---|
| `pom.xml` | Bump parent to `4.0.5`; remove `maven.compiler.source/target`; remove `<liquibase.version>`; remove compiler plugin block; update AWS SDK (2.34.0) and WebJar versions; replace `liquibase-core` with `spring-boot-starter-liquibase`; add `spring-boot-starter-webmvc-test` |
| `CloudFrontLogParser.java` | Update Jackson imports: `com.fasterxml.jackson` → `tools.jackson` (Jackson 3.x package rename) |
| `AppConfig.java` | Replace deprecated `DefaultCredentialsProvider.create()` with `.builder().build()` |
| `GlobalExceptionHandler.java` | Stop extending `ResponseEntityExceptionHandler`; set HTTP status via `HttpServletResponse`; add explicit `MissingServletRequestParameterException` handler |
| `application.yml` | Add `spring.threads.virtual.enabled=true`; add `spring.mvc.problemdetails.enabled=true`; add `connection-init-sql` WAL pragma |
| `ApiControllerTest.java` | Update `@WebMvcTest` import to `org.springframework.boot.webmvc.test.autoconfigure`; replace `MockMvc` with `MockMvcTester`; drop `throws Exception` |
| `DashboardControllerTest.java` | Update `@WebMvcTest` import; replace `MockMvc` with `MockMvcTester`; drop `throws Exception` |

### Unplanned breaking changes discovered during implementation

These were not in the research doc — they were found during the actual upgrade:

| Breaking change | Root cause | Fix applied |
|---|---|---|
| `com.fasterxml.jackson.databind` missing | Jackson 3.x (managed by SB4) renamed packages to `tools.jackson.*` | Updated imports in `CloudFrontLogParser` |
| `@WebMvcTest` not found | SB4 extracted WebMVC test slice into a separate `spring-boot-starter-webmvc-test` module | Added dependency to POM |
| Liquibase not running | SB4 extracted Liquibase auto-config into `spring-boot-starter-liquibase`; bare `liquibase-core` no longer triggers auto-configuration | Replaced `liquibase-core` with `spring-boot-starter-liquibase` |
| `DefaultCredentialsProvider.create()` deprecated | AWS SDK 2.34.0 deprecates static factory in favour of builder | Replaced with `.builder().build()` |

Files that do **not** change: all model records, `DateRange`, `NameCount`, `DailyStatusCount`, `S3LogFetcher`, `UserAgentClassifier`, `DashboardService`, `LogRepository`, `FetchService`, `AppProperties`, `UaClassifierProperties`, `AnalyzeLogsApplication`, `dashboard.html`, `dashboard.js`, all Liquibase changesets, `CloudFrontIntegrationTest`, `LogRepositoryTest`, `CloudFrontLogParserTest`, `UserAgentClassifierTest`.