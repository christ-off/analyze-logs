# Simplification Plan — analyze-logs

Findings from a full-codebase review (2026-08-02): simplifications, duplicated code, dead code.
This document is written as **step-by-step instructions for an implementing model**. Follow it literally.

## Ground rules (read first)

1. **Do the tasks in order.** They are sorted easiest → hardest. Each task is independent unless stated otherwise.
2. **After every task**, run the relevant test suite and only continue when it is green:
   - Java changes: `./mvnw test`
   - JS changes: `npm test`
   - Template changes: `./mvnw test` (controller tests render templates) + start the app and eyeball the page.
3. **One commit per task**, message format: `refactor: <task title>`.
4. **Do not change behavior.** Every task here is a pure refactor except where explicitly marked "behavior note".
5. If an exact "before" snippet below does not match the file anymore, **stop and re-read the file** — do not guess.
6. Line numbers were correct at review time; trust the code snippets over the line numbers.

---

## Part A — Dead code removal (trivial)

### A1. Remove unused `.badge` CSS rule

**File:** `src/main/resources/static/css/app.css` (~line 495)

No template or JS anywhere produces a `badge` class (verified by grep). Delete this whole block including its section comment:

```css
/* ── Badges ───────────────────────────────────────────────────────────────── */
.badge {
  font-weight: 600;
  letter-spacing: .02em;
}
```

### A2. Remove unused `dbPath` component from `AppProperties`

**File:** `src/main/java/com/example/analyzelog/config/AppProperties.java`

`dbPath` is never read from Java code. The `${app.db-path:logs.db}` placeholder in `application.yml` reads the YAML key directly and does **not** need the record component.

- Remove the line `String dbPath,` from the record.
- **Do NOT touch** `application.yml` (the `app.db-path` key stays — the datasource URL uses it), the README, or the tests that set `app.db-path` (they also feed the placeholder).

### A3. Remove `resolveCountryDisplayOrNull` (DashboardService)

**File:** `src/main/java/com/example/analyzelog/service/DashboardService.java`

The only caller immediately converts `null` to `"-"`, which is exactly what `resolveCountryDisplay(iso, "-")` does.

Before (inside `BOT_UA_REQUEST_MAPPER`):

```java
String iso = rs.getString("country");
String countryName = resolveCountryDisplayOrNull(iso);
if (countryName == null) countryName = "-";
```

After:

```java
String countryName = resolveCountryDisplay(rs.getString("country"), "-");
```

Then delete the now-unused method:

```java
private static String resolveCountryDisplayOrNull(String iso) {
    return resolveCountryDisplay(iso, null);
}
```

### A4. Remove the `RESULT_TYPE_SUMS` alias field (DashboardService)

**File:** `src/main/java/com/example/analyzelog/service/DashboardService.java`

This private field is a pure alias:

```java
private static final String RESULT_TYPE_SUMS = ResultTypeSql.RESULT_TYPE_SUMS;
```

Delete it and replace every bare use of `RESULT_TYPE_SUMS` in this class with `ResultTypeSql.RESULT_TYPE_SUMS` (the constructor's `sqlUriByResultType`, `SQL_DAILY_SELECT`, `topBots`, `uaResultTypesByFilter`, `countryResultTypesByFilter`, `topCountriesByFilteredRatio`, `uaRawUserAgents`). Note `browserConfigFetches` already uses the qualified form — that is the target style.

---

## Part B — Java duplicates and simplifications

### B1. `totalCount()` duplicates `NameResultTypeCount.total()`

**File:** `src/main/java/com/example/analyzelog/service/DashboardService.java`

The record `NameResultTypeCount` already has `total()` with the identical formula. Delete:

```java
private static long totalCount(NameResultTypeCount c) {
    return c.hit() + c.miss() + c.function() + c.error();
}
```

and replace the two method references `DashboardService::totalCount` (in `humanTrafficStats` and `countryHumanTrafficStats`) with `NameResultTypeCount::total`.

### B2. `humanTrafficStats` / `countryHumanTrafficStats` have identical bodies

**File:** `src/main/java/com/example/analyzelog/service/DashboardService.java`

Both methods differ only in the first line. Extract a helper (do this AFTER B1):

```java
private static HumanTrafficStats humanStatsFrom(List<NameResultTypeCount> categories) {
    long total = categories.stream().mapToLong(NameResultTypeCount::total).sum();
    long human = categories.stream()
            .filter(c -> "Probable human".equals(c.name()))
            .mapToLong(NameResultTypeCount::total)
            .sum();
    return new HumanTrafficStats(human, total);
}
```

Then the two public methods become:

```java
// Reuses the "Probable human" (client_ip, user_agent) pair classification, scoped to one UA.
public HumanTrafficStats humanTrafficStats(String ua, Instant from, Instant to) {
    return humanStatsFrom(trafficCategories("user_agent = ?", List.of(ua), from, to, false));
}

// Reuses the "Probable human" (client_ip, user_agent) pair classification, scoped to one country.
public HumanTrafficStats countryHumanTrafficStats(String country, Instant from, Instant to) {
    return humanStatsFrom(trafficCategories(country, from, to, false));
}
```

### B3. `countryResultTypes` re-implements `queryResultTypesByFilter`

**File:** `src/main/java/com/example/analyzelog/service/DashboardService.java`

The hand-built SQL in `countryResultTypes` is byte-for-byte what `queryResultTypesByFilter("country", …)` generates (same SELECT, same arg order `from, to, value`). Replace the whole body:

```java
public List<NameCount> countryResultTypes(String countryCode, Instant from, Instant to, boolean excludeBots) {
    String exclusion = excludeClause(humanTrafficClause, excludeBots);
    return queryResultTypesByFilter("country", countryCode, from, to, exclusion);
}
```

**Careful:** keep `humanTrafficClause` here (NOT `RESULT_TYPE_EXCLUSION`, which is what the UA variant uses). `DashboardServiceIntegrationTest` covers this method — it must stay green.

### B4. Merge the two identical exception handlers

**File:** `src/main/java/com/example/analyzelog/web/GlobalExceptionHandler.java`

`handleBadRequest` and `handleMissingParam` have the same body. Replace both with one handler:

```java
@ExceptionHandler({IllegalArgumentException.class, MissingServletRequestParameterException.class})
public String handleBadRequest(Exception ex,
                               HttpServletRequest request,
                               HttpServletResponse response) {
    log.warn("Bad request [{}]: {}", request.getRequestURI(), ex.getMessage());
    response.setStatus(HttpStatus.BAD_REQUEST.value());
    return ERROR_VIEW;
}
```

Keep `handleError` (the catch-all) unchanged.

### B5. Simplify date math in `deleteOldLogs`

**File:** `src/main/java/com/example/analyzelog/repository/LogRepository.java`

Before:

```java
Instant cutoff = Instant.from(
        ZonedDateTime.now(ZoneOffset.UTC).minus(nbMonthsToKeep, ChronoUnit.MONTHS));
String cutoffStr = cutoff.toString();
return jdbc.update(
        "DELETE FROM cloudfront_logs WHERE timestamp < ?",
        cutoffStr);
```

After:

```java
String cutoff = ZonedDateTime.now(ZoneOffset.UTC).minusMonths(nbMonthsToKeep).toInstant().toString();
return jdbc.update("DELETE FROM cloudfront_logs WHERE timestamp < ?", cutoff);
```

Remove the now-unused `java.time.temporal.ChronoUnit` and (if unused) `java.time.Instant` imports.

### B6. Kill the dead switch in `DetailControllerBase.requestRange`

**File:** `src/main/java/com/example/analyzelog/web/DetailControllerBase.java`

Every caller passes `requestRange(null, from, to)` where `from`/`to` are required `@RequestParam`s, so the `switch` can never execute — and it duplicates `DateRangeController.resolveRange`. Replace the method:

```java
protected DateRange requestRange(String from, String to) {
    return DateRange.fromParams(from, to);
}
```

Then update **every** call site from `requestRange(null, from, to)` to `requestRange(from, to)` in these files (count of call sites in parentheses):

- `web/UaDetailController.java` (5)
- `web/CountryDetailController.java` (5)
- `web/UrlDetailController.java` (4)
- `web/RefererDetailController.java` (2)
- `web/CategoryDetailController.java` (2)
- `web/AiBotsController.java` (3)

`./mvnw test` must stay green — the controller tests exercise all these endpoints.

### B7. De-duplicate the four identical format arguments in `trafficCategories`

**File:** `src/main/java/com/example/analyzelog/service/DashboardService.java`

The SQL template receives `ResultTypeSql.resultTypeSums("c")` four times. Use indexed placeholders instead. In the text block, the four lines:

```
       %s,
       %s,
       %s,
       %s
```

become:

```
       %3$s,
       %3$s,
       %3$s,
       %3$s
```

and the first two `%s` placeholders become `%1$s` and `%2$s`. The `.formatted(...)` call then passes only three arguments:

```java
""".formatted(
CATEGORY_CASE_EXPR,
whereAfterRange,
ResultTypeSql.resultTypeSums("c")
);
```

**Careful:** once ANY placeholder is indexed, index ALL of them (`%1$s`, `%2$s`, `%3$s`) or `formatted` mis-assigns arguments. Verify with `DashboardServiceIntegrationTest`.

### B8. Share the country display-name logic (service + controller duplicate it)

**Files:**
- new: `src/main/java/com/example/analyzelog/util/CountryNames.java`
- `src/main/java/com/example/analyzelog/service/DashboardService.java`
- `src/main/java/com/example/analyzelog/web/DashboardController.java`

Create the util:

```java
package com.example.analyzelog.util;

import java.util.Locale;

public final class CountryNames {

    private CountryNames() {}

    /** English display name for an ISO country code; fallback when blank, the code itself when unknown. */
    public static String display(String iso, String fallback) {
        if (iso == null || iso.isBlank()) return fallback;
        String display = Locale.of("", iso).getDisplayCountry(Locale.ENGLISH);
        return (display != null && !display.isBlank()) ? display : iso;
    }
}
```

In `DashboardService`: delete `resolveCountryDisplay` and `resolveCountryLabel`; replace calls with `CountryNames.display(iso, iso)` (where `resolveCountryLabel(iso)` was used) and `CountryNames.display(iso, "-")` (from task A3). Remove the unused `java.util.Locale` import if nothing else uses it.

In `DashboardController.countryDetail`, replace:

```java
String displayName = Locale.of("", country).getDisplayCountry(Locale.ENGLISH);
model.addAttribute("countryCode", country);
model.addAttribute("countryName", displayName.isBlank() ? country : displayName);
```

with:

```java
model.addAttribute("countryCode", country);
model.addAttribute("countryName", CountryNames.display(country, country));
```

(Equivalent logic: blank/unknown codes fall back to the code itself.) Remove the `java.util.Locale` import from the controller.

---

## Part C — JavaScript

Run `npm test` after each task. Some test files mock `utils.js` (`vi.mock`) — if a test fails with "export not found", add the new export to the mock object; do not change test assertions.

### C1. Add a `detailUrl` helper; delete ~10 hand-built URL strings

**File:** `src/main/resources/static/js/utils.js` — add:

```js
export function detailUrl(path, params) {
    const from = Charts.toDateParam(readMeta('cf-from'));
    const to   = Charts.toDateParam(readMeta('cf-to'));
    return `${path}?` + new URLSearchParams({ ...params, from, to }).toString();
}
```

Then replace every arrow function of the shape

```js
d => `/ua-detail?ua=${encodeURIComponent(d.name)}&from=${Charts.toDateParam(from)}&to=${Charts.toDateParam(to)}`
```

with

```js
d => detailUrl('/ua-detail', { ua: d.name })
```

Occurrences to replace (import `detailUrl` from `./utils.js` in each file):

| File | URLs to replace |
|---|---|
| `dashboard.js` | `/ua-detail` (key `ua`), `/country-detail` (key `country`, value `item.code`), `/url-detail` (key `url`), `/referer-detail` (key `referer`), `/category-detail` (key `category`) |
| `bot-analysis.js` | body of `uaDetailUrl()` and `countryDetailUrl()` |
| `url-detail.js` | `/country-detail` (value `item.code`), `/ua-detail` |
| `country-detail.js` | `/category-detail` |
| `category-detail.js` | `/ua-detail` |
| `ai-bots.js` | `/ua-detail`, `/url-detail` |

After replacing, the module-level `const from = readMeta('cf-from'); const to = readMeta('cf-to');` lines in these files may become unused — delete them only if nothing else in the file uses them.

**Behavior note:** `URLSearchParams` encodes spaces as `+` instead of `%20`; Spring decodes both identically, and the existing `uaRequestsUrl` helper already uses `URLSearchParams`, so this is consistent.

### C2. Extract the duplicated clickable-chart options in `charts.js`

**File:** `src/main/resources/static/js/charts.js`

The identical `onClick`/`onHover` spread block appears in both `Charts.horizontalBar` and `Charts.horizontalStackedBar`. Add:

```js
Charts.clickableOptions = function (data, urlFn) {
    if (!urlFn) return {};
    return {
        onClick: (event, elements) => {
            if (!elements.length) return;
            globalThis.location.href = urlFn(data[elements[0].index]);
        },
        onHover: (event, elements) => {
            event.native.target.style.cursor = elements.length ? 'pointer' : 'default';
        },
    };
};
```

and in both chart builders replace the whole `...(urlFn ? { onClick: …, onHover: … } : {})` block with:

```js
...Charts.clickableOptions(data, urlFn),
```

`charts.test.js` covers click/hover behavior — it must stay green.

### C3. Use the `CHART_IDS` destroy idiom in `bot-analysis.js`

**File:** `src/main/resources/static/js/bot-analysis.js`

Before (in `loadAllCharts`):

```js
const topBotsChart = Chart.getChart('chartTopBots');
if (topBotsChart) topBotsChart.destroy();
const filteredChart = Chart.getChart('chartCountriesFiltered');
if (filteredChart) filteredChart.destroy();
```

After — add near the top of the file (same pattern as the other 5 page scripts):

```js
const CHART_IDS = ['chartTopBots', 'chartCountriesFiltered'];
```

and in `loadAllCharts`:

```js
CHART_IDS.forEach(id => Chart.getChart(id)?.destroy());
```

### C4. Merge the three copies of the fetch-into-table pattern in `bot-analysis.js`

**File:** `src/main/resources/static/js/bot-analysis.js`

`loadBotTable`, `loadSimpleTable`, and `loadDisobedientSection` all repeat the same fetch → empty-row → error-row skeleton. Also, `loadSimpleTable`'s `onRendered` parameter is never passed by any caller (dead parameter).

Replace all three with one helper (rows are rendered from the **whole** array so callers can compute `maxTotal`):

```js
function loadTable(url, tbodyId, cols, renderRows, emptyMsg) {
    fetch(url)
        .then(r => r.json())
        .then(data => {
            const tbody = document.getElementById(tbodyId);
            if (!tbody) return;
            tbody.innerHTML = data.length === 0
                ? `<tr><td colspan="${cols}" class="text-center text-muted">${emptyMsg}</td></tr>`
                : renderRows(data);
        })
        .catch(() => {
            const tbody = document.getElementById(tbodyId);
            if (tbody) tbody.innerHTML = `<tr><td colspan="${cols}" class="text-center text-muted py-3">Failed to load data.</td></tr>`;
        });
}
```

Callers become:

```js
function loadProbableBots() {
    const p = buildBaseParams({});
    loadTable('/api/probable-bots?' + p, 'probableBotsTable', 3, data => {
        const maxTotal = Math.max(...data.map(resultTotal));
        return data.map(bot => `<tr>
            <td><a href="${uaRequestsUrl(bot.name)}">${escapeHtml(bot.name)}</a></td>
            <td class="text-end">${resultTotal(bot).toLocaleString()}</td>
            <td class="align-middle px-2">${stackedBar(bot, maxTotal)}</td>
        </tr>`).join('');
    }, 'No extless-only bots found for the selected date range.');
}
```

`loadFakeBrowsers` and `loadBrowserConfigFetches` keep their existing row templates, wrapped as `data => data.map(b => `…`).join('')`. `loadDisobedientSection` becomes a `loadTable('/api/robots-disobedient?' + p, 'disobedientBotsTable', 3, …, 'No disobedient bots found. Try refreshing robots.txt first.')` call and the separate `loadDisobedientBots` function is deleted.

**Careful:** keep each table's exact empty-message text and colspan (3 / 4 / 3 / 3) — `bot-analysis.test.js` asserts on rendered DOM.

---

## Part D — Thymeleaf templates (biggest win, most care needed)

The ~25-line range toolbar (Today / 7 days / 30 days / 3 months buttons + custom date form + bot toggle) is copy-pasted in **9 templates**, and the "human traffic" card in 2. The project already uses fragments (`fragments/flash.html`) — extend that pattern.

### D1. Create `src/main/resources/templates/fragments/toolbar.html`

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head><title>Toolbar fragments</title></head>
<body>

<!-- Range selector + custom date form (+ optional bot toggle).
     basePath:  page URL, e.g. '/country-detail'
     paramName: extra query param name (e.g. 'country') or null
     paramValue: its value (ignored when paramName is null)
     showToggle: true to render the "Hide bots" switch -->
<div th:fragment="rangeToolbar(basePath, paramName, paramValue, showToggle)"
     class="d-flex flex-wrap align-items-center gap-2"
     th:with="suffix=${paramName != null ? '&' + paramName + '=' + #uris.escapeQueryParam(paramValue) : ''}">
    <span class="fw-semibold me-2">Range:</span>
    <a th:href="${basePath} + '?range=1d' + ${suffix}"  th:classappend="${activeRange == '1d'}  ? ' active' : ''" class="btn btn-sm btn-outline-secondary">Today</a>
    <a th:href="${basePath} + '?range=7d' + ${suffix}"  th:classappend="${activeRange == '7d'}  ? ' active' : ''" class="btn btn-sm btn-outline-secondary">7 days</a>
    <a th:href="${basePath} + '?range=30d' + ${suffix}" th:classappend="${activeRange == '30d'} ? ' active' : ''" class="btn btn-sm btn-outline-secondary">30 days</a>
    <a th:href="${basePath} + '?range=3m' + ${suffix}"  th:classappend="${activeRange == '3m'}  ? ' active' : ''" class="btn btn-sm btn-outline-secondary">3 months</a>

    <form th:action="${basePath}" method="get" class="d-flex align-items-center gap-2 ms-2">
        <input type="hidden" th:if="${paramName != null}" th:name="${paramName}" th:value="${paramValue}"/>
        <label for="fromDate" class="form-label mb-0 fw-semibold">Custom:</label>
        <input type="date" id="fromDate" name="from" class="form-control form-control-sm" th:value="${fromDate}" style="width:150px"/>
        <span>&ndash;</span>
        <label for="toDate" class="visually-hidden">To date</label>
        <input type="date" id="toDate" name="to" class="form-control form-control-sm" th:value="${toDate}" style="width:150px"/>
        <button type="submit" class="btn btn-sm btn-primary">Apply</button>
    </form>

    <div th:if="${showToggle}" class="form-check form-switch ms-3 d-flex align-items-center gap-2">
        <input class="form-check-input" type="checkbox" role="switch" aria-checked="false"
               id="toggleBots" style="width:2.5em; height:1.5em"/>
        <label class="form-check-label fw-semibold" for="toggleBots">Hide bots, apps &amp; feeds &amp; noise</label>
    </div>
</div>

<!-- Human traffic proportion card. stats: a HumanTrafficStats object -->
<div th:fragment="humanTraffic(stats)" class="card mb-4">
    <div class="card-body d-flex align-items-center gap-2">
        <span class="fw-semibold">Proportion of requests from human IPs:</span>
        <span th:text="${#numbers.formatDecimal(stats.percentage(), 1, 1)} + '%'"></span>
        <span class="text-muted small"
              th:text="'(' + ${stats.humanRequests()} + ' / ' + ${stats.totalRequests()} + ' requests)'"></span>
    </div>
</div>

</body>
</html>
```

### D2. Replace the copy in each template

In each template below, find the inner `<div class="d-flex flex-wrap align-items-center gap-2">…</div>` that contains the Range links, the custom-date form, and (where present) the bot toggle, and replace that **entire div** with one line:

```html
<div th:replace="~{fragments/toolbar :: rangeToolbar('<basePath>', <paramName>, <paramValue>, <showToggle>)}"></div>
```

Parameters per template:

| Template | basePath | paramName | paramValue | showToggle |
|---|---|---|---|---|
| `dashboard.html` | `'/'` | `null` | `null` | `true` |
| `ua-detail.html` | `'/ua-detail'` | `'ua'` | `${uaName}` | `true` |
| `country-detail.html` | `'/country-detail'` | `'country'` | `${countryCode}` | `true` |
| `url-detail.html` | `'/url-detail'` | `'url'` | `${urlName}` | `true` |
| `referer-detail.html` | `'/referer-detail'` | `'referer'` | `${refererName}` | `true` |
| `category-detail.html` | `'/category-detail'` | `'category'` | `${categoryName}` | `true` |
| `bot-analysis.html` | `'/bot-analysis'` | `null` | `null` | `true` |
| `ai-bots.html` | `'/ai-bots'` | `null` | `null` | `false` |
| `ua-requests.html` | `'/ua-requests'` | `'ua'` | `${ua}` | `false` |

**Special cases — read carefully:**

- **`dashboard.html`**: the toolbar shares its flex container (`card-body d-flex …`) with the "Refresh from S3" section (`ms-auto`). Keep the card-body flex classes and the refresh `div` exactly as they are; replace only the range links + custom form + toggle with the fragment call. The fragment div becomes one flex child, the refresh div stays the second (`ms-auto` still pushes it right). Verify visually.
- **Duplicate `id` warning**: the per-page date-input ids (`fromDateCountry`, `fromDateUa`, `fromDateUrl`, …) all become `fromDate`/`toDate` from the fragment. That is fine (one toolbar per page) — but make sure no leftover element on the same page still uses `fromDate`/`toDate` ids.
- **Intentional unification**: some copies use `<span>&ndash;</span>` + `visually-hidden` label, `ua-requests.html` uses a visible `<label>&ndash;</label>`. The fragment standardizes on the accessible variant. This is a deliberate, harmless markup change.
- **Verify each page manually** after conversion: range buttons keep the extra param (e.g. `country=US`), "Apply" submits the right path, active button is highlighted, bot toggle still reloads charts.

### D3. Use the `humanTraffic` fragment

In `country-detail.html` and `ua-requests.html`, replace the whole "Human traffic indicator" card:

```html
<!-- Human traffic indicator -->
<div class="card mb-4">
    <div class="card-body d-flex align-items-center gap-2">
        <span class="fw-semibold">Proportion of requests from human IPs:</span>
        ...
</div>
```

with:

```html
<div th:replace="~{fragments/toolbar :: humanTraffic(${humanTrafficStats})}"></div>
```

---

## Part E — Optional / needs judgment (do NOT do these blindly)

These are real findings but each needs either a design decision or touches tests. Skip them unless the user asks, or ask first.

### E1. Two sources of truth for "Feeds"

`DashboardService.FEED_URI_LIST` hardcodes `'/feed.xml','/rss.xml'` while `application.yml` defines the same list under `uri-stem-groups → Feeds`. If the YAML changes, the traffic-category classification silently diverges. Proper fix: build the category CASE expression in the `DashboardService` constructor from `UriStemGroupProperties` (find the group named `Feeds`) instead of a static constant — this converts `CATEGORY_CASE_EXPR` and `categoryPairFilter()` from static to instance members. Medium-sized refactor; `DashboardServiceIntegrationTest` covers the behavior.

### E2. `EdgeLocationResolver.Location.countryCode` / `pricingRegion` are unused in production

Only `EdgeLocationResolverTest` reads them. Removing them means also editing that test. Low value — acceptable to leave as-is.

### E3. `js/pages/ua-requests.js` is a 9-line wrapper in an odd location

It only calls `initIpLookup()` and is the sole file under `js/pages/`. Options: move it to `js/ua-requests.js` (update the `<script>` src in `ua-requests.html` AND the import path in `src/test/js/ua-requests.test.js`), or leave it. Check both references with grep before moving.

### E4. Bug watch (not a refactor): the refresh progress bar markup/JS mismatch

`dashboard.html` uses a native `<progress id="refreshBar" max="100">` element, but `dashboard.js` drives it like a Bootstrap div: it sets `style.width`, `aria-valuenow`, and toggles `progress-bar-striped`/`bg-success`/`bg-danger` classes — none of which work on a native `<progress>` (it needs its `value` attribute set). Fix is either: (a) set `bar.value = pct` in `setWidth()` and drop the class fiddling, or (b) restore the Bootstrap `<div class="progress-bar">` markup. Requires a visual check of the "Refresh from S3" flow; decide with the user.

### E5. Two parallel controller base classes

After task B6, `DetailControllerBase.requestRange` is one line and `DateRangeController` serves the MVC pages. They could merge into a single base class, but the split (REST vs MVC) is defensible. Leave unless the user wants it.

---

## Final verification checklist

After all tasks:

1. `./mvnw test` — all green.
2. `npm test` — all green.
3. Start the app, click through: dashboard → a country → a UA → a URL → a referer → a category → bot-analysis → ai-bots → a ua-requests page → admin. On each page: range buttons work and keep their entity param, custom date form works, bot toggle (where present) reloads charts.
4. `git diff --stat` — expect roughly: DashboardService −60 lines, templates −200 lines, JS −60 lines, no new behavior.
