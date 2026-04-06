# Implementation Plan: User Agent Detail View

## Overview

7 phases. No DB migration needed — all data already exists in `cloudfront_logs`.

---

## Phase 1 — Service: 3 new query methods in `DashboardService`

### 1a — `uaResultTypes`

```java
public List<NameCount> uaResultTypes(String uaName, Instant from, Instant to) {
    return jdbc.query("""
            SELECT edge_response_result_type as name, COUNT(*) as count
            FROM cloudfront_logs
            WHERE timestamp BETWEEN ? AND ?
              AND ua_name = ?
            GROUP BY edge_response_result_type
            ORDER BY count DESC
            """,
            (rs, _) -> new NameCount(rs.getString("name"), rs.getLong(COUNT_FIELD)),
            from.toString(), to.toString(), uaName);
}
```

### 1b — `uaCountries`

```java
public List<NameCount> uaCountries(String uaName, Instant from, Instant to) {
    return jdbc.query("""
            SELECT country as name, COUNT(*) as count
            FROM cloudfront_logs
            WHERE timestamp BETWEEN ? AND ?
              AND ua_name = ?
            GROUP BY country
            ORDER BY count DESC
            LIMIT 10
            """,
            (rs, _) -> {
                String iso = rs.getString("name");
                String display = (iso != null && !iso.isBlank())
                        ? Locale.of("", iso).getDisplayCountry(Locale.ENGLISH)
                        : null;
                return new NameCount(display != null ? display : iso, rs.getLong(COUNT_FIELD));
            },
            from.toString(), to.toString(), uaName);
}
```

### 1c — `uaUriStems`

Reuses the same dynamic `NOT LIKE` exclusion clause as `topAllowedUriStems()`, adding
`AND ua_name = ?` in the WHERE clause. The `ua_name` argument is appended between the
date range args and the `limit` arg.

```java
public List<NameCount> uaUriStems(String uaName, Instant from, Instant to, int limit) {
    String exclusionClause = excludedExtensions.stream()
            .map(_ -> "uri_stem NOT LIKE ?")
            .collect(Collectors.joining(" AND "));
    String sql = """
            SELECT uri_stem as name, COUNT(*) as count
            FROM cloudfront_logs
            WHERE timestamp BETWEEN ? AND ?
              AND ua_name = ?
            """
            + (exclusionClause.isEmpty() ? "" : "  AND " + exclusionClause + "\n")
            + """
            GROUP BY uri_stem
            ORDER BY count DESC
            LIMIT ?
            """;

    var args = new ArrayList<>();
    args.add(from.toString());
    args.add(to.toString());
    args.add(uaName);
    excludedExtensions.forEach(ext -> args.add("%." + ext.replaceFirst("^\\.", "")));
    args.add(limit);

    return jdbc.query(sql,
            (rs, _) -> new NameCount(rs.getString("name"), rs.getLong(COUNT_FIELD)),
            args.toArray());
}
```

---

## Phase 2 — API: `UaDetailController`

New `@RestController` at `/api/ua-detail`. Receives `ua`, `from`, `to` as request params.

```java
package com.example.analyzelog.web;

import com.example.analyzelog.model.DateRange;
import com.example.analyzelog.model.NameCount;
import com.example.analyzelog.service.DashboardService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ua-detail")
public class UaDetailController {

    private static final int TOP_LIMIT = 10;
    private final DashboardService dashboardService;

    public UaDetailController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/result-types")
    public List<NameCount> resultTypes(
            @RequestParam String ua,
            @RequestParam String from,
            @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.uaResultTypes(ua, range.from(), range.to());
    }

    @GetMapping("/countries")
    public List<NameCount> countries(
            @RequestParam String ua,
            @RequestParam String from,
            @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.uaCountries(ua, range.from(), range.to());
    }

    @GetMapping("/uri-stems")
    public List<NameCount> uriStems(
            @RequestParam String ua,
            @RequestParam String from,
            @RequestParam String to) {
        var range = DateRange.fromParams(from, to);
        return dashboardService.uaUriStems(ua, range.from(), range.to(), TOP_LIMIT);
    }
}
```

---

## Phase 3 — MVC: `DashboardController` new route

Add `GET /ua-detail` handler alongside the existing `GET /`:

```java
@GetMapping("/ua-detail")
public String uaDetail(
        @RequestParam String ua,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        Model model) {
    DateRange range = (from != null && to != null)
            ? DateRange.fromParams(from, to)
            : DateRange.lastDays(7);
    model.addAttribute("uaName", ua);
    model.addAttribute("from", range.fromIso());
    model.addAttribute("to", range.toIso());
    model.addAttribute("fromDate", range.fromDate().toString());
    model.addAttribute("toDate", range.toDate().toString());
    return "ua-detail";
}
```

---

## Phase 4 — Template: `ua-detail.html`

Mirrors `dashboard.html` structure. Uses `<meta>` tags to pass `from`, `to`, `ua` to JS.
The `th:href` on the Back link preserves the active date range.

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title th:text="'UA Detail – ' + ${uaName}">UA Detail</title>
    <link rel="stylesheet" th:href="@{/webjars/bootstrap/css/bootstrap.min.css}"/>
    <meta name="cf-from" th:content="${from}"/>
    <meta name="cf-to"   th:content="${to}"/>
    <meta name="cf-ua"   th:content="${uaName}"/>
</head>
<body class="bg-light">

<nav class="navbar navbar-dark bg-dark mb-4">
    <div class="container-fluid">
        <a class="navbar-brand" th:href="@{/}">CloudFront Logs</a>
    </div>
</nav>

<main class="container-fluid px-4">

    <!-- description bar -->
    <div class="card mb-4">
        <div class="card-body d-flex align-items-center gap-3">
            <div>
                <h5 class="mb-0" th:text="${uaName}">User Agent</h5>
                <small class="text-muted">
                    <span th:text="${fromDate}"></span> → <span th:text="${toDate}"></span>
                </small>
            </div>
            <a th:href="@{/(from=${fromDate},to=${toDate})}"
               class="btn btn-sm btn-outline-secondary ms-auto">← Back</a>
        </div>
    </div>

    <!-- row 1: two pies -->
    <div class="row g-4 mb-4">
        <div class="col-xl-6">
            <div class="card h-100">
                <div class="card-header fw-semibold">Result Types</div>
                <div class="card-body"><canvas id="chartResultTypes"></canvas></div>
            </div>
        </div>
        <div class="col-xl-6">
            <div class="card h-100">
                <div class="card-header fw-semibold">Countries</div>
                <div class="card-body"><canvas id="chartCountries"></canvas></div>
            </div>
        </div>
    </div>

    <!-- row 2: top URIs -->
    <div class="row g-4 mb-4">
        <div class="col-xl-12">
            <div class="card h-100">
                <div class="card-header fw-semibold">Top URLs</div>
                <div class="card-body"><canvas id="chartUriStems"></canvas></div>
            </div>
        </div>
    </div>

</main>

<script th:src="@{/webjars/bootstrap/js/bootstrap.bundle.min.js}"></script>
<script th:src="@{/webjars/chart.js/dist/chart.umd.js}"></script>
<script th:src="@{/js/ua-detail.js}"></script>
</body>
</html>
```

---

## Phase 5 — JavaScript: `ua-detail.js`

Standalone IIFE. Duplicates `COLORS` and `horizontalBar` from `dashboard.js` (small, avoids
a module system). Adds `pie()` and a `RESULT_TYPE_COLORS` map keyed on the raw result type
string so the pie slices use the same colour palette as the stacked bar charts.

```js
(function () {
    'use strict';

    const from  = document.querySelector('meta[name="cf-from"]').content;
    const to    = document.querySelector('meta[name="cf-to"]').content;
    const ua    = document.querySelector('meta[name="cf-ua"]').content;

    function toDateParam(iso) { return iso.substring(0, 10); }

    const params = new URLSearchParams({ from: toDateParam(from), to: toDateParam(to) });

    const COLORS = {
        blue:   'rgba(54, 162, 235, 0.8)',
        red:    'rgba(220, 53, 69, 0.8)',
        orange: 'rgba(253, 126, 20, 0.8)',
        green:  'rgba(40, 167, 69, 0.8)',
        purple: 'rgba(111, 66, 193, 0.8)',
    };

    const RESULT_TYPE_COLORS = {
        'Hit':                       COLORS.green,
        'Miss':                      COLORS.blue,
        'FunctionGeneratedResponse': COLORS.orange,
        'FunctionExecutionError':    COLORS.orange,
        'FunctionThrottledError':    COLORS.orange,
        'Error':                     COLORS.red,
        'Redirect':                  COLORS.purple,
    };

    function pie(canvasId, data, colorMap) {
        const ctx = document.getElementById(canvasId);
        if (!ctx) return;
        new Chart(ctx, {
            type: 'pie',
            data: {
                labels: data.map(d => d.name ?? '(unknown)'),
                datasets: [{
                    data: data.map(d => d.count),
                    backgroundColor: data.map(d => (colorMap && colorMap[d.name]) ?? COLORS.blue),
                }]
            },
            options: { responsive: true }
        });
    }

    function horizontalBar(canvasId, data) {
        const ctx = document.getElementById(canvasId);
        if (!ctx) return;
        new Chart(ctx, {
            type: 'bar',
            data: {
                labels: data.map(d => d.name ?? '(unknown)'),
                datasets: [{ label: 'Requests', data: data.map(d => d.count),
                             backgroundColor: COLORS.blue }]
            },
            options: {
                indexAxis: 'y',
                responsive: true,
                plugins: { legend: { display: false } },
                scales: { x: { beginAtZero: true } }
            }
        });
    }

    async function loadChart(endpoint, render) {
        try {
            const resp = await fetch(`/api/${endpoint}`);
            if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
            render(await resp.json());
        } catch (e) {
            console.error(`Failed to load ${endpoint}:`, e);
        }
    }

    const uaParams = new URLSearchParams({ ua, from: toDateParam(from), to: toDateParam(to) });

    loadChart(`ua-detail/result-types?${uaParams}`, d => pie('chartResultTypes', d, RESULT_TYPE_COLORS));
    loadChart(`ua-detail/countries?${uaParams}`,    d => pie('chartCountries',   d, null));
    loadChart(`ua-detail/uri-stems?${uaParams}`,    d => horizontalBar('chartUriStems', d));
})();
```

---

## Phase 6 — Click navigation in `dashboard.js`

Add `onClick` and `onHover` to `horizontalStackedBar()` options:

```js
function horizontalStackedBar(canvasId, data) {
    const ctx = document.getElementById(canvasId);
    if (!ctx) return;
    new Chart(ctx, {
        type: 'bar',
        data: {
            labels: data.map(d => d.name),
            datasets: resultTypeDatasets(data),
        },
        options: {
            indexAxis: 'y',
            responsive: true,
            onClick: (event, elements) => {
                if (!elements.length) return;
                const uaName = data[elements[0].index].name;
                window.location.href =
                    `/ua-detail?ua=${encodeURIComponent(uaName)}&from=${toDateParam(from)}&to=${toDateParam(to)}`;
            },
            onHover: (event, elements) => {
                event.native.target.style.cursor = elements.length ? 'pointer' : 'default';
            },
            scales: {
                x: { stacked: true, beginAtZero: true },
                y: { stacked: true }
            }
        }
    });
}
```

---

## Phase 7 — Tests

### `UaDetailControllerTest` (new, `@WebMvcTest`)

Three tests — one per endpoint — mock `DashboardService`, assert HTTP 200 + JSON shape.

```java
@Test
void resultTypesReturnsJson() {
    when(dashboardService.uaResultTypes(eq("Chrome / Windows"), any(), any()))
            .thenReturn(List.of(new NameCount("Hit", 80)));

    assertThat(mvc.get().uri("/api/ua-detail/result-types")
            .param("ua", "Chrome / Windows")
            .param("from", "2026-01-01").param("to", "2026-01-31")
            .exchange())
            .hasStatusOk()
            .bodyJson()
            .extractingPath("$[0].name").isEqualTo("Hit");
}
```

### `DashboardServiceIntegrationTest` additions

Three integration tests, each capturing `Instant from = Instant.now()` before insert and
querying with a narrow window — same isolation pattern already used in the test class.

```java
@Test
void uaResultTypes_countsPerType() {
    Instant from = Instant.now();
    repository.saveEntries("logs/ua-rt-test.gz", List.of(
            entryWithUaAndResultType(UA_CHROME_WINDOWS, "Hit"),
            entryWithUaAndResultType(UA_CHROME_WINDOWS, "Hit"),
            entryWithUaAndResultType(UA_CHROME_WINDOWS, "Error"),
            entryWithUaAndResultType(UA_FIREFOX_LINUX,  "Hit")   // different UA — must not appear
    ));

    var result = dashboardService.uaResultTypes("Chrome / Windows",
            from, Instant.now().plusSeconds(5));

    assertEquals(2, result.stream().filter(n -> "Hit".equals(n.name())).findFirst().orElseThrow().count());
    assertTrue(result.stream().noneMatch(n -> "Miss".equals(n.name())));
}
```

---

## File summary

| Action | File |
|--------|------|
| Edit   | `src/main/java/.../service/DashboardService.java` |
| Create | `src/main/java/.../web/UaDetailController.java` |
| Edit   | `src/main/java/.../web/DashboardController.java` |
| Create | `src/main/resources/templates/ua-detail.html` |
| Create | `src/main/resources/static/js/ua-detail.js` |
| Edit   | `src/main/resources/static/js/dashboard.js` |
| Create | `src/test/java/.../web/UaDetailControllerTest.java` |
| Edit   | `src/test/java/.../service/DashboardServiceIntegrationTest.java` |

---

## TODO

### Phase 1 — Service
- [x] Add `uaResultTypes(String uaName, Instant from, Instant to)`
- [x] Add `uaCountries(String uaName, Instant from, Instant to)`
- [x] Add `uaUriStems(String uaName, Instant from, Instant to, int limit)`

### Phase 2 — API
- [x] Create `UaDetailController` with `/result-types`, `/countries`, `/uri-stems`

### Phase 3 — MVC
- [x] Add `GET /ua-detail` handler to `DashboardController`

### Phase 4 — Template
- [x] Create `ua-detail.html` with description bar, two pie charts, top URIs bar

### Phase 5 — JavaScript
- [x] Create `ua-detail.js` with `pie()`, `horizontalBar()`, `COLORS`, `RESULT_TYPE_COLORS`

### Phase 6 — Click navigation
- [x] Add `onClick` / `onHover` to `horizontalStackedBar()` in `dashboard.js`

### Phase 7 — Tests
- [x] Create `UaDetailControllerTest` (3 endpoint tests)
- [x] Add `uaResultTypes`, `uaCountries`, `uaUriStems` integration tests to `DashboardServiceIntegrationTest`
