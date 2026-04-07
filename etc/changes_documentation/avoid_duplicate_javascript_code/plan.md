# Plan: Extract Shared JavaScript Code

## Problem

The three page scripts share significant duplicated code:

| Code                   | dashboard.js | ua-detail.js | country-detail.js |
|------------------------|:---:|:---:|:---:|
| `COLORS`               | ✓   | ✓   | ✓   |
| `toDateParam`          | ✓   | ✓   | ✓   |
| `loadChart`            | ✓*  | ✓   | ✓   |
| `RESULT_TYPE_COLORS`   |     | ✓   | ✓   |
| `PALETTE`              |     | ✓   |     |
| `pie`                  |     | ✓   | ✓** |
| `horizontalBar`        | ✓*  | ✓   | ✓   |
| `linePerDay`           |     | ✓   | ✓   |

\* `dashboard.js` versions have slight differences (see below).  
\*\* `country-detail.js` `pie` lacks the PALETTE fallback present in `ua-detail.js` — a bug that gets fixed for free by unification.

## Approach

Follow the namespace pattern from the conversation: a single `charts.js` file exposes a `Charts` global object loaded before any page script. No build tooling needed.

---

## Step 1 — Create `src/main/resources/static/js/charts.js`

Move the following into a `Charts` namespace:

```js
const Charts = {};
```

### 1a. Constants

```js
Charts.COLORS = {
    blue:   'rgba(54, 162, 235, 0.8)',
    red:    'rgba(220, 53, 69, 0.8)',
    orange: 'rgba(253, 126, 20, 0.8)',
    green:  'rgba(40, 167, 69, 0.8)',
    purple: 'rgba(111, 66, 193, 0.8)',
};

Charts.RESULT_TYPE_COLORS = {
    'Hit':                       Charts.COLORS.green,
    'Miss':                      Charts.COLORS.blue,
    'FunctionGeneratedResponse': Charts.COLORS.orange,
    'FunctionExecutionError':    Charts.COLORS.orange,
    'FunctionThrottledError':    Charts.COLORS.orange,
    'Error':                     Charts.COLORS.red,
    'Redirect':                  Charts.COLORS.purple,
};

Charts.PALETTE = [
    'rgba(54, 162, 235, 0.8)',
    'rgba(255, 193, 7, 0.8)',
    'rgba(40, 167, 69, 0.8)',
    'rgba(220, 53, 69, 0.8)',
    'rgba(111, 66, 193, 0.8)',
    'rgba(253, 126, 20, 0.8)',
    'rgba(32, 201, 151, 0.8)',
    'rgba(232, 62, 140, 0.8)',
    'rgba(102, 16, 242, 0.8)',
    'rgba(13, 202, 240, 0.8)',
];
```

### 1b. Utility

```js
Charts.toDateParam = function(iso) { return iso.substring(0, 10); };
```

### 1c. `loadChart` — unified signature

The current `dashboard.js` appends `?${params}` inside `loadChart`; the detail pages embed params in the endpoint string.  
Unify by always embedding params in the endpoint (callers own the full query string):

```js
Charts.loadChart = async function(endpoint, render) {
    try {
        const resp = await fetch(`/api/${endpoint}`);
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
        render(await resp.json());
    } catch (e) {
        console.error(`Failed to load ${endpoint}:`, e);
    }
};
```

`dashboard.js` callers change from `loadChart('ua-names-split', ...)` to
`loadChart('ua-names-split?' + params, ...)`.

### 1d. Chart renderers

```js
// Simple horizontal bar; urlFn is optional — enables click navigation
Charts.horizontalBar = function(canvasId, data, urlFn) { ... };

// Pie with colorMap or PALETTE fallback
Charts.pie = function(canvasId, data, colorMap) { ... };

// Multi-series line chart (requests per day)
Charts.linePerDay = function(canvasId, data) { ... };
```

`horizontalBar` becomes the unified version from `dashboard.js` (with optional `urlFn`),
which is a superset of the plain version in the detail pages.

---

## Step 2 — Update `dashboard.js`

Remove: `COLORS`, `toDateParam`, `horizontalBar` (plain), `loadChart`.

Keep page-specific:
- `resultTypeDatasets(data)`
- `stackedBar(canvasId, data)`
- `horizontalStackedBar(canvasId, data)`

Update `loadChart` calls to embed params in the endpoint string:
```js
const p = params.toString();
Charts.loadChart(`ua-names-split?${p}`,    ...);
Charts.loadChart(`countries?${p}`,         ...);
// etc.
```

---

## Step 3 — Update `ua-detail.js`

Remove: `COLORS`, `RESULT_TYPE_COLORS`, `PALETTE`, `toDateParam`, `pie`, `horizontalBar`, `linePerDay`, `loadChart`.

All calls become `Charts.*`:
```js
Charts.loadChart(`ua-detail/result-types?${uaParams}`, d => Charts.pie('chartResultTypes', d, Charts.RESULT_TYPE_COLORS));
// etc.
```

---

## Step 4 — Update `country-detail.js`

Same as Step 3, substituting `countryParams`.  
The `pie` fix (PALETTE fallback) comes for free from the shared implementation.

---

## Step 5 — Load `charts.js` in templates

In `dashboard.html`, `ua-detail.html`, and `country-detail.html`, add before the page-specific `<script>`:

```html
<script th:src="@{/js/charts.js}"></script>
```

No Thymeleaf fragment changes needed — the three pages do not share a layout fragment today.

---

## File diff summary

| File | Action |
|------|--------|
| `static/js/charts.js` | **Create** — shared constants + renderers + loadChart |
| `static/js/dashboard.js` | **Edit** — remove duplicates, update loadChart calls |
| `static/js/ua-detail.js` | **Edit** — remove duplicates, use Charts.* |
| `static/js/country-detail.js` | **Edit** — remove duplicates, use Charts.* |
| `templates/dashboard.html` | **Edit** — add `charts.js` script tag |
| `templates/ua-detail.html` | **Edit** — add `charts.js` script tag |
| `templates/country-detail.html` | **Edit** — add `charts.js` script tag |