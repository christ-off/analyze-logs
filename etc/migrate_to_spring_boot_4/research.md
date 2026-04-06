# Migration Research: Spring Boot 3.5 → Spring Boot 4

**Date:** 2026-04-06  
**Current version:** Spring Boot 3.5.0 / Spring Framework 6.x / Java 25

---

## 1. Application Overview

**AnalyzeLogs** is a single-user Spring Boot web dashboard for Amazon CloudFront standard logs (JSON format).

### What it does
1. Fetches `.gz` log files from an S3 bucket (AWS SDK v2)
2. Parses CloudFront JSON log lines into `CloudFrontLogEntry` records
3. Persists parsed entries into a local SQLite database via JdbcTemplate
4. Serves an interactive browser dashboard (Thymeleaf + Bootstrap 5 + Chart.js 4) with 4 charts:
   - Top user agents (classified labels)
   - Top blocked countries (403 responses, ISO → display name)
   - Top allowed URLs (status < 400)
   - Requests per day (stacked: 2xx/3xx, 4xx, 5xx)
5. Supports incremental S3 refresh: tracks already-imported files in a `fetched_files` table

### Entry point
`AnalyzeLogsApplication` — plain `@SpringBootApplication` + `@ConfigurationPropertiesScan("com.example.analyzelog.config")`.

---

## 2. Full Technology Stack

| Layer | Technology | Version |
|---|---|---|
| Framework | Spring Boot | 3.5.0 |
| Language | Java | 25 |
| Build | Maven | 3.x |
| Database | SQLite | 3.49.1.0 (xerial driver) |
| Migrations | Liquibase | 4.31.1 (overrides Boot managed) |
| Data access | Spring JDBC / JdbcTemplate | (Boot-managed) |
| Web | Spring MVC | (Boot-managed) |
| Templating | Thymeleaf | (Boot-managed) |
| Frontend CSS | Bootstrap via WebJar | 5.3.3 |
| Frontend charts | Chart.js via WebJar | 4.4.4 |
| WebJar resolution | webjars-locator-lite | (Boot-managed) |
| AWS | AWS SDK v2 (S3 + STS) | 2.30.31 (BOM) |
| JSON parsing | Jackson (ObjectMapper) | (Boot-managed) |
| Logging | Logback (logback-spring.xml) | (Boot-managed) |
| Monitoring | Spring Boot Actuator | (Boot-managed) |
| Testing | JUnit 5, Mockito, MockMvc | (Boot-managed) |

---

## 3. Component Inventory

### Configuration
- `AppProperties` — `@ConfigurationProperties(prefix = "app")` record; holds `AwsProperties` (region, bucket, prefix, profile) and `dbPath`.
- `UaClassifierProperties` — `@ConfigurationProperties(prefix = "ua-classifier")` record; holds 50+ rule pairs (pattern/label).
- `AppConfig` — `@Configuration`; produces `S3Client` bean (uses `ProfileCredentialsProvider` or `DefaultCredentialsProvider`) and `UserAgentClassifier` bean.

### Web Controllers
- `DashboardController` — `GET /`; resolves date range (preset: 1d/7d/30d/3m or custom from/to), passes ISO instants + LocalDate strings to Thymeleaf model.
- `ApiController` — `@RestController` at `/api`; 4 GET endpoints (`/ua-names`, `/countries`, `/uri-stems`, `/requests-per-day`), all accept `from`/`to` ISO date strings.
- `RefreshController` — `GET /refresh`; triggers `FetchService.fetch(null, true)`, flash message on redirect.
- `GlobalExceptionHandler` — `@ControllerAdvice` extending `ResponseEntityExceptionHandler`; maps `IllegalArgumentException` → 400; POST errors → redirect with flash.

### Services
- `DashboardService` — read-only; 4 methods executing parameterized SQL (GROUP BY + ORDER BY COUNT DESC + LIMIT 10); ISO code → display country name via `Locale.of("", iso).getDisplayCountry(Locale.ENGLISH)`.
- `FetchService` — orchestrates S3 list → download → parse → save; uses `AtomicInteger` counters; returns `FetchResult(fetched, skipped, failed)` record.
- `UserAgentClassifier` — rule-first (literal substring match via `Pattern.quote()`, case-insensitive, first-match wins), then browser/OS fallback detection, then `"Unknown"`.

### Data Access
- `LogRepository` — `@Repository`; `isAlreadyFetched(s3Key)` check; `@Transactional saveEntries()` using `jdbc.batchUpdate()` with 21 fields + calls `classifier.classify()` to fill `ua_name`; `getStats()` for totals.

### Fetcher & Parser
- `S3LogFetcher` — `@Component`; `downloadLogFile()` downloads object bytes and decompresses `.gz` inline; `streamLogKeys()` uses `listObjectsV2Paginator()` and streams keys to a `Consumer<String>` callback (avoids materializing full key list).
- `CloudFrontLogParser` — `@Component`; uses Jackson `ObjectMapper` (`static final` shared instance); parses date+time → `Instant`; handles `"-"` → null; URL-decodes user agents; per-line error isolation (returns empty `Optional` on failure).

### Models (all Java records)
- `CloudFrontLogEntry` — 20 fields; maps CloudFront JSON fields.
- `DateRange` — utility record; `lastDays(n)`, `lastMonths(n)`, `fromParams(from, to)` factory methods; `toDate()` subtracts 1 second to convert exclusive midnight to inclusive date.
- `NameCount(String name, long count)` — chart data DTO.
- `DailyStatusCount(LocalDate day, long success, long clientError, long serverError)` — stacked bar chart DTO.

### Database Schema
Two tables, managed by Liquibase:

```
cloudfront_logs
  id, timestamp (TEXT/ISO), edge_location, sc_bytes, client_ip, method, uri_stem,
  status, referer, user_agent, edge_result_type, protocol, cs_bytes, time_taken,
  edge_response_result_type, protocol_version, time_to_first_byte,
  edge_detailed_result_type, content_type, content_length, country, ua_name
  INDEX: idx_cloudfront_logs_timestamp, idx_cloudfront_logs_ua_name

fetched_files
  s3_key (PK), fetched_at
```

Key design choice: `timestamp` stored as ISO TEXT (not native DATE/DATETIME), so all queries use string comparison (`BETWEEN ? AND ?`) which works because ISO 8601 sorts lexicographically. All analytics are raw SQL; no ORM.

### Frontend
- `dashboard.html` — Thymeleaf template; Bootstrap 5; 2×2 chart grid; date range controls (preset buttons + HTML5 date inputs); "Refresh from S3" button (`GET /refresh`); flash fragment.
- `dashboard.js` — IIFE; reads ISO dates from `<meta name="cf-from/to">`; async-fetches 4 API endpoints; renders via Chart.js (`horizontalBar()`, `stackedBar()` helpers).
- All JS/CSS assets served via WebJars (no CDN, no npm build step).

### Tests
- `CloudFrontLogParserTest` — unit; 6 test methods; uses inline string fixtures.
- `UserAgentClassifierTest` — unit; loads real `application.yml` rules; 50+ parameterized cases.
- `ApiControllerTest` — `@WebMvcTest` + mocked `DashboardService`; 6 test methods.
- `DashboardControllerTest` — `@WebMvcTest`; 6 test methods; no mocks.
- `LogRepositoryTest` — `@SpringBootTest` + `@TempDir` for in-memory/temp SQLite; 5 test methods.
- `CloudFrontIntegrationTest` — full integration; uses real `.gz` fixture from `test/resources`; end-to-end parse → save → query.

---

## 4. What Spring Boot 4 Means

Spring Boot 4 will be based on **Spring Framework 7**. As of 2026 it has not been released, but its requirements and breaking changes are well-documented from milestone builds and the Spring team's public roadmap.

### 4.1 Java Version Requirement

Spring Boot 4 / Spring Framework 7 **requires Java 17 as the baseline** and is optimized for **Java 21+** (LTS with virtual threads, records, pattern matching). The project already uses Java 25, which exceeds this requirement — **no change needed here**.

However, the maven-compiler-plugin currently sets `<source>25</source>/<target>25</target>` redundantly alongside the `<java.version>` property. Spring Boot 4's parent POM controls this via `<java.version>` only, so the explicit plugin configuration may need cleaning up.

The `--enable-native-access=ALL-UNNAMED` surefire argLine (needed for SQLite's JNI layer) will likely still be required.

### 4.2 Jakarta EE Namespace

Already migrated in Spring Boot 3 — the codebase uses `jakarta.servlet` throughout (see `GlobalExceptionHandler` importing `jakarta.servlet.http.HttpServletRequest`). **No action needed.**

### 4.3 Spring Framework 7 Breaking Changes

#### Removed deprecated APIs
Spring Framework 7 removes a large number of APIs that were deprecated in 5.x and 6.x. Items to audit:

| Class/API used | Status | Action |
|---|---|---|
| `ResponseEntityExceptionHandler` | Under review in SF7 — signature changes possible | Audit `GlobalExceptionHandler` |
| `JdbcTemplate.batchUpdate()` with `BatchPreparedStatementSetter`-like lambda | Stable | No change |
| `@ConfigurationPropertiesScan` | Stable | No change |
| `@ControllerAdvice` | Stable | No change |
| `RedirectAttributes` | Stable | No change |
| `WebRequest` | Stable | No change |

The key risk is `GlobalExceptionHandler extends ResponseEntityExceptionHandler`. Spring Framework 7 revises how `handleExceptionInternal` works in `ResponseEntityExceptionHandler`. The override at line 48 of `GlobalExceptionHandler.java` may break if the signature changes.

#### HttpServletRequest / HttpServletResponse
Spring MVC 7 continues to support Servlet-based request handling but also introduces a first-class reactive pathway. No change required for this blocking app, but the dependency on `javax.servlet` being replaced with `jakarta.servlet` (already done) is a prerequisite that is already met.

#### `@ResponseStatus` on `@ExceptionHandler`
The combination of `@ExceptionHandler` + `@ResponseStatus` on the same method (as in `handleBadRequest`) has historically been finicky. Spring Boot 4 tightens this — the `@ResponseStatus` annotation may be **ignored** when the handler returns a view name (String) instead of `ResponseEntity`. Confirm behavior in SB4 tests.

### 4.4 Spring Security

This application has **no Spring Security**. Spring Security 7 (the version paired with Spring Boot 4) introduces significant changes to its auto-configuration and the deprecated `WebSecurityConfigurerAdapter` (already removed in SS6) pattern. Since the project doesn't use Spring Security at all, **no impact** unless security is added during migration.

### 4.5 Spring Boot Auto-Configuration Changes

Several auto-configuration class names and property namespaces changed between Boot 3.x and Boot 4.x:

| Area | Change | Impact on this project |
|---|---|---|
| Actuator | Management endpoint properties may be renamed | Low — only default actuator used |
| Thymeleaf | `spring.thymeleaf.*` properties stable | None |
| Liquibase | `spring.liquibase.*` stable; version override pattern may need review | Review `<liquibase.version>` property |
| DataSource / HikariCP | Properties stable | None |
| Jackson | Auto-configuration stable for basic `ObjectMapper` use | Note: `CloudFrontLogParser` uses `static final ObjectMapper` — not Spring-managed; review thread safety under virtual threads |

### 4.6 Liquibase Version

The POM explicitly overrides the Boot-managed Liquibase version:
```xml
<liquibase.version>4.31.1</liquibase.version>
```
Spring Boot 4 will manage a newer Liquibase version. The override comment says "keep 4.31.1" — this reason should be revisited. If Boot 4 manages a Liquibase version > 4.31.1, removing this override and using the managed version is preferred. Test that the two existing changelogs run cleanly.

### 4.7 AWS SDK v2

AWS SDK v2 (2.30.31) is independent of Spring Boot versioning. It will not break from a Spring Boot upgrade. However:

- STS dependency (`software.amazon.awssdk:sts`) is present but `S3LogFetcher` doesn't use STS directly. If it's a transitive pull for credential resolution, it remains needed. Verify it's not dead weight.
- AWS SDK v2 2.x → future 3.x (if it exists by then) would be a separate concern.

### 4.8 WebJars

`webjars-locator-lite` is the Spring Boot 3 replacement for the old `webjars-locator`. Spring Boot 4 continues to support this. However, verify that the Thymeleaf `@{/webjars/...}` expressions in `dashboard.html` still resolve correctly — the WebJar locator behavior is unchanged but Bootstrap/Chart.js WebJar versions may need updates.

The current WebJar paths:
```html
th:href="@{/webjars/bootstrap/css/bootstrap.min.css}"
th:src="@{/webjars/bootstrap/js/bootstrap.bundle.min.js}"
th:src="@{/webjars/chart.js/dist/chart.umd.js}"
```
These resolve correctly with `webjars-locator-lite` in SB 3.5. Confirm they still work in SB4.

### 4.9 SQLite Specifics

SQLite is not a supported database in Spring Boot's auto-configuration. The configuration is manual (`spring.datasource.driver-class-name: org.sqlite.JDBC`, `hikari.maximum-pool-size: 1`). This pattern is unaffected by Spring Boot version.

The `--enable-native-access=ALL-UNNAMED` JVM flag is required because `sqlite-jdbc` uses JNI. This flag must remain in `maven-surefire-plugin` argLine and also in the `java -jar` invocation at runtime.

### 4.10 Virtual Threads

Spring Boot 3.2+ supports virtual threads via `spring.threads.virtual.enabled=true`. Spring Boot 4 likely makes virtual threads the default or at least promotes them heavily. This has implications:

- **JdbcTemplate + SQLite**: SQLite with HikariCP at `maximum-pool-size: 1` means all DB operations serialize through one connection. Under virtual threads, many requests can be waiting on that single connection. This is functionally correct but may cause latency pile-up under load. For a single-user app, this is acceptable.
- **UserAgentClassifier**: Uses `List<Rule>` (immutable after construction) — thread safe.
- **CloudFrontLogParser**: Uses `static final ObjectMapper` — Jackson's `ObjectMapper` is thread safe for reading. No issue.
- **FetchService**: Uses `AtomicInteger` for counters — thread safe.

---

## 5. Migration Plan

### Phase 1 — Dependency Upgrades (no code changes)

1. Bump `spring-boot-starter-parent` to `4.0.0` (once released).
2. Remove or update the `<liquibase.version>` override — use Boot-managed version unless a specific issue requires pinning.
3. Update AWS SDK BOM to latest `2.x` or `3.x` if available.
4. Update `sqlite-jdbc` to latest version.
5. Update Bootstrap and Chart.js WebJar versions as appropriate.
6. Run `mvn clean test` — treat test failures as the migration checklist.

### Phase 2 — Fix Breaking Compile Errors

1. **`GlobalExceptionHandler`**: Check if `ResponseEntityExceptionHandler.handleExceptionInternal()` signature changed. If `HttpStatusCode`/`WebRequest` parameter types changed, update accordingly.
2. **`@ResponseStatus` + `@ExceptionHandler` combination**: Test `handleBadRequest` returns HTTP 400 correctly. If `@ResponseStatus` is ignored when returning a view name, switch to returning `ResponseEntity<Void>` or a `ModelAndView` with explicit status.
3. Scan for any removed/renamed Spring annotations or classes using `mvn dependency:analyze` and compiler output.

### Phase 3 — Configuration Review

1. Audit all `application.yml` property keys against Spring Boot 4's property migration guide.
2. Particularly review `spring.thymeleaf.*`, `spring.liquibase.*`, `management.*` namespaces.
3. Verify `spring.datasource.hikari.maximum-pool-size: 1` still applies (HikariCP property names have been stable).

### Phase 4 — Test & Validate

1. Run full test suite: `mvn clean verify`.
2. Manually test `/refresh` S3 flow (requires live AWS credentials).
3. Manually test all 4 chart endpoints with various date ranges.
4. Verify Liquibase changelogs run cleanly on a fresh database.
5. Verify WebJar asset resolution for Bootstrap and Chart.js.

### Phase 5 — Optional Enhancements during Migration

These are not breaking changes but are natural improvements while the code is open:

- Enable virtual threads: `spring.threads.virtual.enabled=true` — verify SQLite connection pool behavior.
- Add Spring Security (HTTP Basic or form login) — long-deferred given the current no-auth posture.
- Consider `@EnableScheduling` + a `@Scheduled` refresh task instead of manual `/refresh` endpoint.

---

## 6. Risk Assessment

| Risk | Severity | Likelihood | Notes |
|---|---|---|---|
| `GlobalExceptionHandler` signature breakage | Medium | Medium | Extending `ResponseEntityExceptionHandler` is a common migration pain point |
| `@ResponseStatus` ignored on String-returning handler | Low | Medium | Easy to fix; returns 200 with view instead of 400 |
| Liquibase version incompatibility | Low | Low | Project uses standard Liquibase XML; no exotic features |
| WebJar locator behavior change | Low | Low | `webjars-locator-lite` is the SB3+ recommended approach |
| SQLite JDBC JNI flag requirement | Low | Low | Must keep `--enable-native-access=ALL-UNNAMED` |
| AWS SDK v2 incompatibility | None | Very low | AWS SDK is Spring-independent |
| Jackson `static final ObjectMapper` | None | None | Thread-safe for reads; no issue |

---

## 7. Files to Watch During Migration

| File | Why |
|---|---|
| `pom.xml` | Parent version bump, Liquibase override |
| `GlobalExceptionHandler.java` | Extends potentially-changed SB class |
| `application.yml` | Property key changes |
| `logback-spring.xml` | Logback profile syntax stable, but verify |
| `src/main/resources/templates/dashboard.html` | WebJar path resolution |
| `CloudFrontIntegrationTest.java` | Full-stack test; most likely to catch regressions |

---

## 8. What Will NOT Need Changes

- All Java records (`CloudFrontLogEntry`, `DateRange`, `NameCount`, `DailyStatusCount`, `AppProperties`, `UaClassifierProperties`, `FetchService.FetchResult`, `LogRepository.Stats`) — Java language feature, not Spring.
- `CloudFrontLogParser` — pure Java; no Spring APIs beyond `@Component`.
- `S3LogFetcher` — pure AWS SDK + Java IO; `@Component` annotation only.
- `UserAgentClassifier` — pure Java; no Spring APIs.
- `DashboardService` — uses `JdbcTemplate` which is stable.
- `LogRepository` — uses `JdbcTemplate.batchUpdate()` and `@Transactional`; both stable.
- `FetchService` — uses `@Service` and constructor injection; both stable.
- `dashboard.js` — client-side; not affected by server framework version.
- All Liquibase changesets — XML format; not version-sensitive at this level.
- SQLite schema — no changes required.
- AWS configuration pattern (profile + region) — unchanged.