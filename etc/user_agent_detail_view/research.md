# Research: User Agent Detail View

## Goal

When the user clicks a bar in the "Top User Agents" chart, open a dedicated detail page
showing — for that specific user agent:
- A pie chart of `edge_response_result_type` distribution
- A pie chart of country distribution (using the `country` column)
- A ranked list of top requested `uri_stem` values

---

## Click handling on Chart.js horizontal stacked bar

Chart.js exposes an `onClick` option on every chart instance. For a horizontal stacked bar
the clicked element carries an `index` into the labels array, which maps directly to the
`data[idx].name` UA label.

Add to `horizontalStackedBar()` options:

```js
onClick: (event, elements) => {
    if (!elements.length) return;
    const idx = elements[0].index;
    const uaName = data[idx].name;
    window.location.href =
        `/ua-detail?ua=${encodeURIComponent(uaName)}&from=${toDateParam(from)}&to=${toDateParam(to)}`;
},
onHover: (event, elements) => {
    event.native.target.style.cursor = elements.length ? 'pointer' : 'default';
},
```

This covers **bar segment clicks** and works without any additional library.
Clicking the Y-axis label (the UA name text) does **not** trigger `onClick` in Chart.js —
label click interception requires a custom plugin and is disproportionately complex.
The bar click is sufficient and is the expected interaction.

---

## URL scheme

```
GET /ua-detail?ua=<encoded-ua-name>&from=2026-01-01&to=2026-01-31
```

The `ua` parameter is the `ua_name` value (classifier output, e.g. `"Chrome / Windows"`),
not the raw user agent string. It is URL-encoded by `encodeURIComponent`.

The date range is forwarded so the detail page shows the same window as the dashboard.

---

## New Thymeleaf page — `ua-detail.html`

Served by `DashboardController` at `GET /ua-detail`. The controller passes:
- `uaName` — display title
- `from`, `to` — ISO instants (same pattern as dashboard)
- `fromDate`, `toDate` — date strings for the date inputs

The page layout mirrors `dashboard.html`:
- Navbar with "← Back to Dashboard" link
- A description card showing the UA name and the active date range
- Two pie charts side-by-side
- A horizontal bar for top URIs below

```html
<!-- description bar -->
<div class="card mb-4">
    <div class="card-body">
        <h5 class="mb-1" th:text="${uaName}">User Agent</h5>
        <small class="text-muted">
            <span th:text="${fromDate}"></span> → <span th:text="${toDate}"></span>
        </small>
        <a th:href="@{/?from=${fromDate}&to=${toDate}}" class="btn btn-sm btn-outline-secondary ms-3">
            ← Back to Dashboard
        </a>
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
```

---

## New API endpoints — `UaDetailController`

A dedicated `@RestController` keeps detail concerns separate from `ApiController`.

### `GET /api/ua-detail/result-types`
```
?ua=Chrome+%2F+Windows&from=2026-01-01&to=2026-01-31
→ List<NameCount>  [{"name":"Hit","count":120}, {"name":"Miss","count":30}, ...]
```

### `GET /api/ua-detail/countries`
```
?ua=Chrome+%2F+Windows&from=2026-01-01&to=2026-01-31
→ List<NameCount>  [{"name":"France","count":80}, {"name":"United States","count":40}, ...]
```
Country ISO codes resolved to display names via `Locale` — same as `topBlockedCountries()`.

### `GET /api/ua-detail/uri-stems`
```
?ua=Chrome+%2F+Windows&from=2026-01-01&to=2026-01-31
→ List<NameCount>  [{"name":"/index.html","count":55}, ...]
```
Applies the same extension exclusion filter as `topAllowedUriStems()`.

---

## New service methods — `DashboardService`

All three queries add `AND ua_name = ?` to the existing counterpart queries.

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
            (rs, _) -> new NameCount(rs.getString("name"), rs.getLong("count")),
            from.toString(), to.toString(), uaName);
}

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
                        : iso;
                return new NameCount(display != null ? display : iso, rs.getLong("count"));
            },
            from.toString(), to.toString(), uaName);
}

public List<NameCount> uaUriStems(String uaName, Instant from, Instant to, int limit) {
    // same dynamic NOT LIKE exclusion clause as topAllowedUriStems()
    ...
    // adds: AND ua_name = ?
}
```

---

## New JavaScript — `ua-detail.js`

Reads `ua`, `from`, `to` from `<meta>` tags (same pattern as `dashboard.js`).
Uses `Chart.js` pie chart type for the two distribution charts.

```js
const RESULT_TYPE_COLORS = {
    'Hit':                      COLORS.green,
    'Miss':                     COLORS.blue,
    'FunctionGeneratedResponse': COLORS.orange,
    'FunctionExecutionError':   COLORS.orange,
    'FunctionThrottledError':   COLORS.orange,
    'Error':                    COLORS.red,
    'Redirect':                 COLORS.purple,
};

function pie(canvasId, data) {
    const ctx = document.getElementById(canvasId);
    if (!ctx) return;
    new Chart(ctx, {
        type: 'pie',
        data: {
            labels: data.map(d => d.name ?? '(unknown)'),
            datasets: [{
                data: data.map(d => d.count),
                backgroundColor: data.map(d => RESULT_TYPE_COLORS[d.name] ?? COLORS.blue),
            }]
        },
        options: { responsive: true }
    });
}

loadChart(`ua-detail/result-types?ua=${encodeURIComponent(ua)}&${params}`,
          data => pie('chartResultTypes', data));
loadChart(`ua-detail/countries?ua=${encodeURIComponent(ua)}&${params}`,
          data => pie('chartCountries', data));
loadChart(`ua-detail/uri-stems?ua=${encodeURIComponent(ua)}&${params}`,
          data => horizontalBar('chartUriStems', data));
```

The country pie uses the same `COLORS.blue` fallback since countries have no semantic colour.

---

## `COLORS` sharing

`COLORS` and `horizontalBar` are currently defined inside `dashboard.js`'s IIFE and are not
accessible from `ua-detail.js`. Two options:

**Option A — duplicate** the `COLORS` map and `horizontalBar` function in `ua-detail.js`.
Simple, no build tooling needed.

**Option B — extract** shared code to `common.js` loaded before both scripts.

Option A is recommended — the duplication is small (6 lines for `COLORS`, 15 for
`horizontalBar`) and avoids introducing a module system or script load-order dependency
with no other use case in sight.

---

## `DashboardController` change

Add a new handler for the detail page:

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

## Files to create / change

| Action | File |
|--------|------|
| Edit   | `src/main/resources/static/js/dashboard.js` — add `onClick`/`onHover` to `horizontalStackedBar` |
| Create | `src/main/resources/static/js/ua-detail.js` |
| Create | `src/main/resources/templates/ua-detail.html` |
| Edit   | `src/main/java/.../web/DashboardController.java` — add `GET /ua-detail` |
| Create | `src/main/java/.../web/UaDetailController.java` — 3 API endpoints |
| Edit   | `src/main/java/.../service/DashboardService.java` — 3 new methods |
| Create | `src/test/java/.../web/UaDetailControllerTest.java` |
| Edit   | `src/test/java/.../service/DashboardServiceIntegrationTest.java` — 3 new tests |
