# Plan — Issue #3: Merge "Top Allowed URLs" & "Top Blocked URLs" into "Top URLs"

## Goal
Replace the two separate URL charts on the main dashboard with a single **"Top URLs"** chart using the standard result-type stacked bar coloring (Hit / Miss / Function / Redirect / Error).

---

## Current state

| Layer | "Top Allowed URLs" | "Top Blocked URLs" |
|---|---|---|
| Canvas | `chartUriStems` | `chartTopUriStems` |
| JS fetch | `/api/uri-stems` | `/api/top-uri-stems` |
| API endpoint | `ApiController#uriStems` | `ApiController#topUriStems` |
| Service method | `DashboardService#topAllowedUriStems` | `DashboardService#topUriStems` |
| Return type | `List<NameCount>` | `List<NameCount>` |
| SQL filter | `status < 400` | none (all) + Wordpress/PHP grouping |
| Chart type | `Charts.horizontalBar()` (single color) | `Charts.horizontalBar()` (single color) |

---

## Impacted files

### 1. `DashboardService.java`
- **Add** `topUrlsByResultType(Instant from, Instant to, int limit) → List<NameResultTypeCount>`
  - Reuse the Wordpress/PHP `CASE` grouping from `SQL_GROUPED_URI_CASE`
  - Apply `uriStemExclusionClause()` filter (same as existing methods)
  - Select result-type breakdown using `RESULT_TYPE_SUMS`
  - Order by `(hit + miss + function + error + redirect) DESC`
  - No `status` filter — all requests included
- **Remove** `topAllowedUriStems()` and `topUriStems()` once replaced

### 2. `ApiController.java`
- **Add** `GET /api/top-urls-split` → calls `topUrlsByResultType`, returns `List<NameResultTypeCount>`
- **Remove** `GET /api/uri-stems` and `GET /api/top-uri-stems`

### 3. `dashboard.html`
- **Remove** one of the two URL `<div class="col-xl-6">` cards in row 2
- **Rename** remaining card header to "Top URLs"
- **Replace** canvas id with `chartTopUrls`
- Row 2 becomes a single full-width card (`col-xl-12`) or keep `col-xl-6` paired with another chart

### 4. `dashboard.js`
- **Remove** the two `Charts.loadChart` lines for `uri-stems` and `top-uri-stems`
- **Add** one call: `Charts.loadChart('top-urls-split?${p}', data => horizontalStackedBar('chartTopUrls', data))`
- **Update** `horizontalStackedBar` click handler: currently navigates to `/ua-detail` — for URLs there is no detail page, so **remove the `onClick`/`onHover` from this call** (pass a flag or extract a variant without navigation)

### 5. `ApiControllerTest.java`
- **Remove** tests for `/api/uri-stems` and `/api/top-uri-stems`
- **Add** test for `/api/top-urls-split` (valid range → 200 + JSON array)

### 6. `DashboardServiceIntegrationTest.java`
- **Remove** tests for `topAllowedUriStems()` and `topUriStems()`
- **Add** test for `topUrlsByResultType()`: verifies non-null result, correct field types, Wordpress/PHP grouping

---

## Implementation order

1. `DashboardService` — add `topUrlsByResultType`, keep old methods until wired up
2. `ApiController` — add `/api/top-urls-split`, remove old endpoints
3. `DashboardService` — remove `topAllowedUriStems` and `topUriStems`
4. `dashboard.html` — merge cards, new canvas id
5. `dashboard.js` — replace two fetches with one stacked-bar call
6. Tests — update `ApiControllerTest` and `DashboardServiceIntegrationTest`

---

## Notes
- `NameResultTypeCount` record already exists and covers all needed fields.
- `RESULT_TYPE_SUMS` SQL constant is already shared — reuse directly.
- No DB schema changes, no Liquibase migration needed.
- The `horizontalStackedBar` function in `dashboard.js` currently hardcodes navigation to `/ua-detail`. Either extract a no-click variant or add an optional `urlFn` parameter (consistent with `Charts.horizontalBar`).

---

## Implementation summary

Implemented in commit on 2026-04-08. All 139 tests pass.

### Changes made

**`DashboardService.java`**
- Added `SQL_URI_BY_RESULT_TYPE` constant: same Wordpress/PHP `CASE` grouping as `SQL_GROUPED_URI_CASE` combined with `RESULT_TYPE_SUMS` (placed after `RESULT_TYPE_SUMS` to avoid forward reference)
- Added `SQL_URI_RESULT_TYPE_GROUP_ORDER` constant: groups by name, orders by total requests DESC
- Added `topUrlsByResultType(Instant, Instant, int) → List<NameResultTypeCount>`
- Removed `topAllowedUriStems()` and `topUriStems()`

**`ApiController.java`**
- Added `GET /api/top-urls-split` → `topUrlsByResultType`
- Removed `GET /api/uri-stems` and `GET /api/top-uri-stems`

**`dashboard.html`**
- Row 2: replaced two `col-xl-6` URL cards with a single `col-xl-12` "Top URLs" card (`chartTopUrls` canvas)

**`dashboard.js`**
- `horizontalStackedBar(canvasId, data, urlFn)`: made `urlFn` optional via spread operator — no click handler when omitted
- UA chart call updated to pass `urlFn` navigating to `/ua-detail`
- Replaced `uri-stems` + `top-uri-stems` fetches with single `top-urls-split` fetch using `horizontalStackedBar` (no navigation)

**`ApiControllerTest.java`**
- Replaced `uriStemsReturnsJson` with `topUrlsSplitReturnsJson` (checks name + hit fields)

**`DashboardServiceIntegrationTest.java`**
- Replaced `topAllowedUriStems_excludesStaticExtensions` with `topUrlsByResultType_excludesStaticExtensions`
- Replaced `topUriStems_aggregatesPhpAndWordpress` with `topUrlsByResultType_aggregatesPhpAndWordpress` (uses total count across result types)
- Added `topUrlsByResultType_countsResultTypesAndAggregatesPhpWordpress` (verifies per-type counts for Hit/Miss/Error/Redirect)
- Added `entryWithUriAndResultType` helper