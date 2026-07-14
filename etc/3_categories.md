# Plan: "Traffic Categories" bar chart (3 categories)

## Goal
Add a new stacked bar chart to the main dashboard classifying distinct **(client_ip, user_agent)**
pairs into 3 mutually-exclusive categories, split by the standard Hit/Miss/Filtered/Error coloring.
Designed so the same chart can be dropped onto other detail pages (ua-detail, country-detail,
url-detail, referer-detail) later with a scoping filter.

## Category definitions (evaluated per distinct client_ip + user_agent pair, over the whole date range)

Priority order (a pair falls into the first matching category):

1. **Probable human** — the pair has requested at least one URL ending in `/` (a "page") **and**
   at least one image asset or the JS bundle.
   - "ending with /" → `uri_stem LIKE '%/'`
   - "image" → extension in `.jpg,.jpeg,.png,.gif,.webp,.avif,.svg,.ico`
   - "js bundle" → `uri_stem LIKE '/js/%'` (any file served from the app's JS bundle folder)
2. **Declared bots** (better term for "normal bots" — open to renaming, alternatives:
   "Compliant bots" / "Well-behaved bots") — the pair requested `/robots.txt` (and didn't
   already qualify as category 1).
3. **Other** — everything else (neither of the above).

Date range and the existing "Hide bots, apps & feeds & noise" toggle (`excludeBots`) apply exactly
like on every other dashboard chart, via the existing `humanTrafficClause`.

## Backend

### `DashboardService`
Add a new public method, written in the same reusable style as `urlsByResultType(...)`:

```java
public List<NameResultTypeCount> trafficCategories(Instant from, Instant to, boolean excludeBots) {
    return trafficCategories("", List.of(), from, to, excludeBots);
}

// package-private, extra filter reserved for later reuse (e.g. "ua_name = ?", "country = ?")
private List<NameResultTypeCount> trafficCategories(String additionalFilter, List<Object> extraArgs,
                                                     Instant from, Instant to, boolean excludeBots) { ... }
```

SQL shape (SQLite):

```sql
WITH pair_class AS (
    SELECT client_ip, user_agent,
        CASE
            WHEN MAX(CASE WHEN uri_stem LIKE '%/' THEN 1 ELSE 0 END) = 1
             AND MAX(CASE WHEN <image-ext-predicate> OR uri_stem LIKE '/js/%' THEN 1 ELSE 0 END) = 1
                THEN 'Probable human'
            WHEN MAX(CASE WHEN uri_stem = '/robots.txt' THEN 1 ELSE 0 END) = 1
                THEN 'Declared bots'
            ELSE 'Other'
        END AS category
    FROM cloudfront_logs
    WHERE timestamp BETWEEN ? AND ?
      [AND <excludeBots: humanTrafficClause>]
      [AND <additionalFilter>]
    GROUP BY client_ip, user_agent
)
SELECT pc.category AS name,
       SUM(CASE WHEN c.edge_response_result_type = 'Hit'  THEN 1 ELSE 0 END) AS hit,
       SUM(CASE WHEN c.edge_response_result_type = 'Miss' THEN 1 ELSE 0 END) AS miss,
       SUM(CASE WHEN c.edge_response_result_type IN (<function-types>) THEN 1 ELSE 0 END) AS function,
       SUM(CASE WHEN c.edge_response_result_type = 'Error' THEN 1 ELSE 0 END) AS error
FROM cloudfront_logs c
JOIN pair_class pc ON c.client_ip = pc.client_ip AND c.user_agent = pc.user_agent
WHERE c.timestamp BETWEEN ? AND ?
  [AND <same excludeBots / additionalFilter, aliased on c>]
GROUP BY pc.category
ORDER BY CASE pc.category WHEN 'Probable human' THEN 0 WHEN 'Declared bots' THEN 1 ELSE 2 END
```

Notes:
- Reuses the existing `NameResultTypeCount` model (`name, hit, miss, function, error`) — **no new
  model class needed**, matches "standard coloring scheme for hit/miss/filtered/error" requirement.
- Reuses existing constants: `ResultTypeSql.FUNCTION_TYPE_LIST`, `humanTrafficClause`,
  `excludeClause(...)`.
- Image extension predicate built once as a private constant (list of `uri_stem LIKE '%.<ext>'` ORed),
  same style as `extensionArgs`/`uriStemExclusionClause` already in the class.
- Category labels are fixed literal strings from SQL, always present in a stable order even when
  count is 0 (use `ORDER BY CASE ...` above; if a category has 0 pairs it just won't appear in the
  CTE — acceptable, matches existing chart behavior elsewhere, e.g. `ua-groups`).

### `ApiController`
```java
@GetMapping("/traffic-categories")
public List<NameResultTypeCount> trafficCategories(@RequestParam String from, @RequestParam String to,
                                                    @RequestParam(defaultValue = "false") boolean excludeBots) {
    var range = DateRange.fromParams(from, to);
    return dashboardService.trafficCategories(range.from(), range.to(), excludeBots);
}
```

## Frontend

### `dashboard.html`
Add a new full-width (or half-width, next to an existing pie) card, e.g. as its own row:

```html
<div class="row g-4 mb-4">
    <div class="col-12">
        <div class="card">
            <div class="card-header fw-semibold">Traffic Categories</div>
            <div class="card-body" style="position:relative;height:340px">
                <canvas id="chartTrafficCategories"></canvas>
            </div>
        </div>
    </div>
</div>
```

### `dashboard.js`
- Add `'chartTrafficCategories'` to `CHART_IDS`.
- Load and render with the **existing** `Charts.horizontalStackedBar` (already reusable/generic,
  takes any `{name, hit, miss, function, error}[]` and draws a Hit/Miss/Filtered/Error stacked bar
  with the standard color scheme) — no new charting code required:

```js
Charts.loadChart(`traffic-categories?${p}`, data =>
    Charts.horizontalStackedBar('chartTrafficCategories', data));
```

No `urlFn` (3 fixed categories aren't clickable through to a detail page).

## Reusability for other pages
Because `trafficCategories(...)` is built with the same `additionalFilter` / `extraArgs` shape as
`urlsByResultType`, adding it to `ua-detail`, `country-detail`, `url-detail`, or `referer-detail`
later is just:
- expose a small overload taking the relevant scoping predicate (`ua_name = ?`, `country = ?`, etc.)
- add one `@GetMapping` per page in the matching detail controller (mirrors existing per-page
  endpoints already in `ApiController`)
- drop the same canvas + `Charts.horizontalStackedBar` call into that page's JS file

No changes to `charts.js` are needed for reuse — the stacked-bar renderer is already generic.

## Tests
- `DashboardServiceIntegrationTest`: new test inserting rows covering all 3 categories (a pair with
  `/` + an image, a pair with only `/robots.txt`, a pair with neither) and asserting correct
  bucket + hit/miss/filtered/error counts; include one bot-excluded case via `excludeBots=true`.
- `DashboardControllerTest` (if it covers `/api` endpoints) or a small `ApiControllerTest`: assert
  `/api/traffic-categories` returns the service's list.
- `charts.test.js` / `dashboard.test.js`: no new JS logic to unit test (reusing
  `horizontalStackedBar`), just confirm `chartTrafficCategories` is present in `CHART_IDS` and wired
  in `loadAllCharts`.

## Open decision for the user
- Final label for category 2 ("normal bots"). Plan defaults to **"Declared bots"**; alternatives:
  "Compliant bots", "Well-behaved bots", "robots.txt-abiding bots".
