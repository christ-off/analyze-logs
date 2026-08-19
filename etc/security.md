# Security Dashboard — Implementation Plan

## Context

We're adding a new "Security" dashboard page. It shows traffic that matches known security-scan URL patterns. Today the only scan group defined is `PHP/WordPress` (uri_stems like `/wp-%`, `%.php`, etc. — see `application.yml`'s `uri-stem-groups`). Later, more scan groups will be added and should show up as additional categories on this same page — but that is **not** part of this task. This task only wires up `PHP/WordPress`.

Unlike every other dashboard page, the CloudFront `Hit/Miss/Filtered/Error` result-type split is **not** relevant here — these charts must be colored by **security category** instead (today: a single color for `PHP/WordPress`).

Decisions already confirmed with the user:
- No "Hide bots" toggle on this page (mirrors `/ai-bots`, which also has none).
- Chart bars are clickable and reuse existing detail pages: Traffic Categories → `/url-detail?url=PHP/WordPress`, Top Countries → `/country-detail?country=<code>`.
- New nav entry: **"Security"** at **`/security`**, placed in the sidebar right after "AI Bots".

This plan follows the exact structure of the existing `/ai-bots` page (`AiBotsController`, `ai-bots.html`, `ai-bots.js`) and reuses the existing `uriStemPredicate("PHP/WordPress")` helper in `DashboardService` (already used by `url-detail` to match `wp-*`/`*.php` URIs) instead of inventing new filtering logic.

**IMPORTANT**: Follow every step below exactly, in order. Do not invent alternative approaches, extra abstractions, or additional config not listed here. Copy the existing code patterns referenced (they are named precisely so you can open them and match their style).

---

## Step 1 — Save this plan to `etc/security.md`

Copy this entire plan file's content into a new file at `etc/security.md` in the repo. This is a documentation deliverable the user asked for, not just a working plan.

---

## Step 2 — New model records

Two new files, following the exact style of `src/main/java/com/example/analyzelog/model/NameCount.java`.

**`src/main/java/com/example/analyzelog/model/CountryCount.java`** (new file):
```java
package com.example.analyzelog.model;

public record CountryCount(String code, String name, long count) {}
```

**`src/main/java/com/example/analyzelog/model/DailyCount.java`** (new file):
```java
package com.example.analyzelog.model;

import java.time.LocalDate;

public record DailyCount(LocalDate day, long count) {}
```

Do **not** touch `NameResultTypeCount`, `CountryResultTypeCount`, or `DailyResultTypeCount` — those stay as-is for every other page. The security page reuses the existing `NameCount(name, count)` record for its Traffic Categories chart (a single row, `name="PHP/WordPress"`).

---

## Step 3 — `DashboardService` additions

Open `src/main/java/com/example/analyzelog/service/DashboardService.java`.

### 3a. Add imports

Add these two imports next to the existing `model.*` imports near the top of the file:
```java
import com.example.analyzelog.model.CountryCount;
import com.example.analyzelog.model.DailyCount;
```

### 3b. Add two new RowMappers

Add these next to the existing `COUNTRY_RESULT_TYPE_COUNT_MAPPER` and `DAILY_RESULT_TYPE_COUNT_MAPPER` field declarations (around line 71):

```java
private static final RowMapper<CountryCount> COUNTRY_COUNT_MAPPER =
        (rs, _) -> {
            String iso = rs.getString("code");
            return new CountryCount(iso, resolveCountryLabel(iso), rs.getLong(COUNT_FIELD));
        };
private static final RowMapper<DailyCount> DAILY_COUNT_MAPPER =
        (rs, _) -> new DailyCount(LocalDate.parse(rs.getString("day")), rs.getLong(COUNT_FIELD));
```

(`resolveCountryLabel` and `COUNT_FIELD` already exist in this class — reuse them, do not redefine.)

### 3c. Add a constant for the category name

Add near the other `private static final String` constants at the top of the class (e.g. next to `COUNTRY_FILTER`):
```java
private static final String SECURITY_PHP_WORDPRESS = "PHP/WordPress";
```

### 3d. Add three new public methods

Add these right after the existing `trafficCategories(...)` methods (after the block ending around the `categoryPairFilter()` method / before `AI_BOTS_FILTER`). They all reuse the existing private `uriStemPredicate(String urlName)` method — **do not** write new pattern-matching SQL by hand, call `uriStemPredicate(SECURITY_PHP_WORDPRESS)` exactly like `urlTopCountriesByResultType` does.

```java
public List<NameCount> securityTrafficCategories(Instant from, Instant to) {
    var entry = uriStemPredicate(SECURITY_PHP_WORDPRESS);
    String sql = "SELECT '" + SECURITY_PHP_WORDPRESS + "' as name, COUNT(*) as count\n" +
            "FROM cloudfront_logs\n" +
            "WHERE timestamp BETWEEN ? AND ?\n" +
            "  AND " + entry.getKey() + "\n";
    var args = new ArrayList<>();
    args.add(from.toString());
    args.add(to.toString());
    args.addAll(entry.getValue());
    return jdbc.query(sql, NAME_COUNT_MAPPER, args.toArray());
}

public List<CountryCount> securityTopCountries(Instant from, Instant to, int limit) {
    var entry = uriStemPredicate(SECURITY_PHP_WORDPRESS);
    String sql = SQL_SELECT_COUNTRY + "COUNT(*) as count\n" +
            "FROM cloudfront_logs\n" +
            "WHERE timestamp BETWEEN ? AND ?\n" +
            "  AND country IS NOT NULL\n" +
            "  AND " + entry.getKey() + "\n" +
            "GROUP BY country\n" +
            "ORDER BY count DESC\n" +
            LIMIT_PARAM;
    var args = new ArrayList<>();
    args.add(from.toString());
    args.add(to.toString());
    args.addAll(entry.getValue());
    args.add(limit);
    return jdbc.query(sql, COUNTRY_COUNT_MAPPER, args.toArray());
}

public List<DailyCount> securityRequestsPerDay(Instant from, Instant to) {
    var entry = uriStemPredicate(SECURITY_PHP_WORDPRESS);
    String sql = "SELECT date(timestamp) as day, COUNT(*) as count\n" +
            "FROM cloudfront_logs\n" +
            "WHERE timestamp BETWEEN ? AND ?\n" +
            "  AND " + entry.getKey() + "\n" +
            "GROUP BY day\n" +
            "ORDER BY day\n";
    var args = new ArrayList<>();
    args.add(from.toString());
    args.add(to.toString());
    args.addAll(entry.getValue());
    return jdbc.query(sql, DAILY_COUNT_MAPPER, args.toArray());
}
```

Notes:
- `SQL_SELECT_COUNTRY` is the existing constant `"SELECT country as code,\n"` — already declared in this class, reuse it.
- `LIMIT_PARAM` is the existing constant `"LIMIT ?\n"` — reuse it.
- `uriStemPredicate("PHP/WordPress")` returns a `Map.Entry<String pattern, List<Object> args>` where the pattern already reads the `PHP/WordPress` group's patterns from `application.yml` — this is exactly how `url-detail` already filters for this same group, so there is nothing new to configure.

---

## Step 4 — New REST controller `SecurityController`

New file: **`src/main/java/com/example/analyzelog/web/SecurityController.java`**, modeled exactly on `AiBotsController.java`:

```java
package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.CountryCount;
import com.example.analyzelog.model.DailyCount;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/security")
class SecurityController extends DetailControllerBase {

    public SecurityController(DashboardService dashboardService, AppProperties appProperties) {
        super(dashboardService, appProperties);
    }

    @GetMapping("/traffic-categories")
    public List<NameCount> trafficCategories(@RequestParam String from, @RequestParam String to) {
        var range = requestRange(null, from, to);
        return dashboardService.securityTrafficCategories(range.from(), range.to());
    }

    @GetMapping("/top-countries")
    public List<CountryCount> topCountries(@RequestParam String from, @RequestParam String to) {
        var range = requestRange(null, from, to);
        return dashboardService.securityTopCountries(range.from(), range.to(), appProperties.topLimit());
    }

    @GetMapping("/requests-per-day")
    public List<DailyCount> requestsPerDay(@RequestParam String from, @RequestParam String to) {
        var range = requestRange(null, from, to);
        return dashboardService.securityRequestsPerDay(range.from(), range.to());
    }
}
```

(`requestRange`, `dashboardService`, `appProperties` are all inherited from `DetailControllerBase` — same as `AiBotsController`.)

---

## Step 5 — Page route in `DashboardController`

Open `src/main/java/com/example/analyzelog/web/DashboardController.java` and add this method, copied from the existing `aiBots(...)` method just above/below it:

```java
@GetMapping("/security")
public String security(
        @RequestParam(required = false) String range,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        Model model) {
    addDateAttributes(model, resolveRange(range, from, to), resolveActiveRange(range, from, to));
    return "security";
}
```

---

## Step 6 — `charts.js` additions

Open `src/main/resources/static/js/charts.js`.

### 6a. Add a category color map

Add near `Charts.RESULT_TYPE_COLORS` (after it):
```js
Charts.SECURITY_CATEGORY_COLORS = {
    'PHP/WordPress': 'rgba(99, 102, 241, 0.85)',
};
```
(Indigo — visually distinct from the green/blue/orange/red result-type colors used everywhere else, so security-category charts are immediately recognizable as a different kind of chart. Feel free to pick a different shade; this is the only place the color is defined.)

### 6b. Make `Charts.horizontalBar` accept an optional color

Find the existing `Charts.horizontalBar = function (canvasId, data, urlFn) {...}`. Change its signature and the one line that sets `backgroundColor`:

```js
Charts.horizontalBar = function (canvasId, data, urlFn, color = Charts.ACCENT) {
    const ctx = document.getElementById(canvasId);
    if (!ctx) return;
    const MAX = 35;
    const truncate = s => s.length > MAX ? s.slice(0, MAX) + '…' : s;
    new Chart(ctx, {
        type: 'bar',
        data: {
            labels: data.map(d => truncate(d.name ?? '(unknown)')),
            datasets: [{
                label: 'Requests',
                data: data.map(d => d.count),
                backgroundColor: color,
            }]
        },
        options: {
            indexAxis: 'y',
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: { x: { beginAtZero: true } },
            ...(urlFn ? {
                onClick: (event, elements) => {
                    if (!elements.length) return;
                    globalThis.location.href = urlFn(data[elements[0].index]);
                },
                onHover: (event, elements) => {
                    event.native.target.style.cursor = elements.length ? 'pointer' : 'default';
                },
            } : {})
        }
    });
};
```

Everything else about the function is unchanged. `color` defaults to `Charts.ACCENT` so every existing caller (which doesn't pass a 4th argument) behaves exactly as before. `backgroundColor` in Chart.js accepts either a single color string (flat) or an array of strings (one per bar) — both are valid values for the `color` parameter, so no extra logic is needed here.

### 6c. Add `Charts.barByDay` — a single-series version of `stackedBarByDay`

Add this new function right after the existing `Charts.stackedBarByDay` function:

```js
Charts.barByDay = function (canvasId, data, color, label) {
    const ctx = document.getElementById(canvasId);
    if (!ctx) return;
    new Chart(ctx, {
        type: 'bar',
        data: {
            labels: data.map(d => d.day),
            datasets: [{ label, data: data.map(d => d.count), backgroundColor: color }]
        },
        options: {
            responsive: true,
            datasets: { bar: { maxBarThickness: 44 } },
            scales: {
                x: {},
                y: { beginAtZero: true }
            }
        }
    });
};
```

Do not modify `Charts.stackedBarByDay` or `Charts.resultTypeDatasets` — those stay exactly as they are, used by every other page.

---

## Step 7 — New template `security.html`

New file: **`src/main/resources/templates/security.html`**. Copy `src/main/resources/templates/ai-bots.html` structure exactly (same layout fragment call, same date-range toolbar with no toggle), but with these three chart cards instead:

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layout :: page('Security', ~{:: .extra-head}, ~{:: .page-content}, ~{:: .page-script})}">
<head><title>Security</title></head>
<th:block class="extra-head">
    <meta name="cf-from" th:content="${from}"/>
    <meta name="cf-to"   th:content="${to}"/>
</th:block>
<th:block class="page-content">

    <!-- Date range bar -->
    <div class="card mb-4 cf-toolbar">
        <div class="card-body d-flex flex-wrap align-items-center gap-2">
            <span class="fw-semibold me-2">Range:</span>
            <a th:href="@{/security(range='1d')}"  th:classappend="${activeRange == '1d'}  ? ' active' : ''" class="btn btn-sm btn-outline-secondary">Today</a>
            <a th:href="@{/security(range='7d')}"  th:classappend="${activeRange == '7d'}  ? ' active' : ''" class="btn btn-sm btn-outline-secondary">7 days</a>
            <a th:href="@{/security(range='30d')}" th:classappend="${activeRange == '30d'} ? ' active' : ''" class="btn btn-sm btn-outline-secondary">30 days</a>
            <a th:href="@{/security(range='3m')}"  th:classappend="${activeRange == '3m'}  ? ' active' : ''" class="btn btn-sm btn-outline-secondary">3 months</a>

            <form th:action="@{/security}" method="get" class="d-flex align-items-center gap-2 ms-2">
                <label for="fromDate" class="form-label mb-0 fw-semibold">Custom:</label>
                <input type="date" id="fromDate" name="from" class="form-control form-control-sm" th:value="${fromDate}" style="width:150px"/>
                <span>&ndash;</span>
                <label for="toDate" class="visually-hidden">To date</label>
                <input type="date" id="toDate" name="to"   class="form-control form-control-sm" th:value="${toDate}"   style="width:150px"/>
                <button type="submit" class="btn btn-sm btn-primary">Apply</button>
            </form>
        </div>
    </div>

    <!-- row 0: traffic categories -->
    <div class="row g-4 mb-4">
        <div class="col-12">
            <div class="card">
                <div class="card-header fw-semibold">Traffic Categories</div>
                <div class="card-body" style="position:relative;height:200px">
                    <canvas id="chartTrafficCategories"></canvas>
                </div>
            </div>
        </div>
    </div>

    <!-- row 1: top countries -->
    <div class="row g-4 mb-4">
        <div class="col-12">
            <div class="card">
                <div class="card-header fw-semibold">Top Countries</div>
                <div class="card-body"><canvas id="chartCountries"></canvas></div>
            </div>
        </div>
    </div>

    <!-- row 2: requests per day -->
    <div class="row g-4 mb-4">
        <div class="col-12">
            <div class="card">
                <div class="card-header fw-semibold">Requests per Day</div>
                <div class="card-body"><canvas id="chartRequestsPerDay"></canvas></div>
            </div>
        </div>
    </div>

</th:block>
<th:block class="page-script">
    <script type="module" th:src="@{/js/security.js}"></script>
</th:block>
</html>
```

---

## Step 8 — New script `security.js`

New file: **`src/main/resources/static/js/security.js`**, modeled on `ai-bots.js`:

```js
import { Charts } from './charts.js';
import { readMeta, buildBaseParams } from './utils.js';

const from = readMeta('cf-from');
const to   = readMeta('cf-to');

const CHART_IDS = ['chartTrafficCategories', 'chartCountries', 'chartRequestsPerDay'];
const PHP_WORDPRESS_COLOR = Charts.SECURITY_CATEGORY_COLORS['PHP/WordPress'];

function loadAllCharts() {
    CHART_IDS.forEach(id => Chart.getChart(id)?.destroy());
    const p = buildBaseParams({});

    Charts.loadChart(`security/traffic-categories?${p}`, data =>
        Charts.horizontalBar('chartTrafficCategories', data,
            d => `/url-detail?url=${encodeURIComponent(d.name)}&from=${Charts.toDateParam(from)}&to=${Charts.toDateParam(to)}`,
            data.map(d => Charts.SECURITY_CATEGORY_COLORS[d.name] ?? Charts.ACCENT)));

    Charts.loadChart(`security/top-countries?${p}`, data =>
        Charts.horizontalBar('chartCountries', data,
            item => `/country-detail?country=${encodeURIComponent(item.code)}&from=${Charts.toDateParam(from)}&to=${Charts.toDateParam(to)}`,
            PHP_WORDPRESS_COLOR));

    Charts.loadChart(`security/requests-per-day?${p}`, data =>
        Charts.barByDay('chartRequestsPerDay', data, PHP_WORDPRESS_COLOR, 'PHP/WordPress'));
}

loadAllCharts();
```

Note: `buildBaseParams({})` also reads the `#toggleBots` checkbox from the DOM (see `utils.js`), but since `security.html` has no such element, `document.getElementById('toggleBots')` returns `null` and it's safely skipped — no `excludeBots` param is ever sent, matching `ai-bots.js`'s existing behavior.

---

## Step 9 — Sidebar nav link

Open `src/main/resources/templates/layout.html`. Add a new link right after the existing "AI Bots" `<a class="cf-nav-link" th:href="@{/ai-bots}" ...>...</a>` block (still inside the `Analytics` nav section, before `<span class="cf-nav-section">System</span>`):

```html
<a class="cf-nav-link" th:href="@{/security}" data-path="/security">
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M12 3l7 3v6c0 4.97-3.5 8.5-7 9-3.5-.5-7-4.03-7-9V6l7-3z"/>
        <path d="m9.5 12 1.8 1.8 3.2-3.6"/>
    </svg>
    Security
</a>
```

---

## Step 10 — Tests

### 10a. `SecurityControllerTest` (new file)

`src/test/java/com/example/analyzelog/web/SecurityControllerTest.java`, modeled exactly on `CategoryDetailControllerTest.java`:

```java
package com.example.analyzelog.web;

import com.example.analyzelog.config.AppProperties;
import com.example.analyzelog.model.CountryCount;
import com.example.analyzelog.model.DailyCount;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebMvcTest(SecurityController.class)
@EnableConfigurationProperties(AppProperties.class)
class SecurityControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    DashboardService dashboardService;

    @Test
    void trafficCategoriesReturnsJson() {
        when(dashboardService.securityTrafficCategories(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new NameCount("PHP/WordPress", 42)));

        assertThat(mvc.get().uri("/api/security/traffic-categories")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].name").isEqualTo("PHP/WordPress");
    }

    @Test
    void topCountriesReturnsJson() {
        when(dashboardService.securityTopCountries(any(Instant.class), any(Instant.class), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(new CountryCount("US", "United States", 10)));

        assertThat(mvc.get().uri("/api/security/top-countries")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].code").isEqualTo("US");
    }

    @Test
    void requestsPerDayReturnsJson() {
        when(dashboardService.securityRequestsPerDay(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new DailyCount(LocalDate.parse("2026-01-05"), 7)));

        assertThat(mvc.get().uri("/api/security/requests-per-day")
                .param("from", "2026-01-01").param("to", "2026-01-31")
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].count").isEqualTo(7);
    }

    @Test
    void invalidDateRangeReturns400() {
        assertThat(mvc.get().uri("/api/security/traffic-categories")
                .param("from", "2026-02-01").param("to", "2026-01-01")
                .exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
```

### 10b. Page route test — add to `DashboardControllerTest.java`

Add these two tests to the existing `DashboardControllerTest` class:
```java
@Test
void securityReturns200() {
    assertThat(mvc.get().uri("/security").exchange())
            .hasStatusOk()
            .hasViewName("security");
}

@Test
void securityDefaultRangeIs7Days() {
    assertThat(mvc.get().uri("/security").exchange())
            .model().containsEntry("activeRange", "7d");
}
```

### 10c. Service-level integration tests — add to `DashboardServiceIntegrationTest.java`

Open the file, find how existing tests insert rows into `cloudfront_logs` and assert against `dashboardService.trafficCategories(...)` / `topEdgeLocations(...)` for the exact insert helper / table schema used, then add three new `@Test` methods following that same insert style:

1. `securityTrafficCategories_countsOnlyPhpWordpressUris` — insert one row with `uri_stem = '/wp-login.php'` and one row with `uri_stem = '/index.html'`; assert `securityTrafficCategories(from, to)` returns exactly one row named `"PHP/WordPress"` whose count is `1` (the `.php`/`/index.html` row must not be counted — check the actual `uri_stem` value used matches one of the configured patterns: `/wp-%`, `//wp-%`, `/wordpress/%`, `/wp/%`, `%.php`, `%.php7`).
2. `securityTopCountries_filtersToPhpWordpressUris` — insert rows with different `country` values, some matching a `PHP/WordPress` pattern and some not; assert only the matching country/count comes back.
3. `securityRequestsPerDay_filtersToPhpWordpressUris` — insert rows across two different days, only some matching; assert the per-day counts only reflect matching rows.

Match the exact JDBC insert / table setup already used by the surrounding tests in this file — do not invent a different way to insert test rows.

---

## Step 11 — Verification

1. `./mvnw test` — all existing and new tests must pass.
2. `./mvnw spring-boot:run`, then in a browser:
   - Sidebar shows a new "Security" link between "AI Bots" and the "System" section; it highlights as active on `/security`.
   - Visit `/security` — date-range toolbar works (Today/7 days/30 days/3 months/custom), no bot-exclude toggle is present.
   - "Traffic Categories" shows one row: `PHP/WordPress`, colored indigo, count roughly matching what the main dashboard's "Top URLs" chart shows for its `PHP/WordPress` grouped bar over the same range. Clicking it navigates to `/url-detail?url=PHP/WordPress&...`.
   - "Top Countries" shows countries generating `PHP/WordPress`-matching requests, same indigo color, clicking a bar navigates to `/country-detail?country=...`.
   - "Requests per Day" shows one indigo series of daily counts.
   - None of the three charts show any Hit/Miss/Filtered/Error breakdown or coloring.
3. `npx playwright test` (if e2e tests are run for other pages) — not required to add a new e2e spec for this task, existing suite should still pass unaffected.
