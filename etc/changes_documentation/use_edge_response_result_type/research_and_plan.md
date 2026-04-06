# Research: Replace HTTP Status Grouping with `edge_response_result_type`

## Goal

Replace the current "Requests per Day" stacked-bar chart — which groups by HTTP status code ranges
(2xx/3xx success, 4xx client error, 5xx server error) — with grouping by the
`edge_response_result_type` column, which carries CloudFront's own semantic classification.

---

## Current implementation

### Model — `DailyStatusCount.java`
```java
public record DailyStatusCount(LocalDate day, long success, long clientError, long serverError) {}
```

### Query — `DashboardService.requestsPerDay()`
```sql
SELECT date(timestamp) as day,
       SUM(CASE WHEN status >= 200 AND status < 400 THEN 1 ELSE 0 END) as success,
       SUM(CASE WHEN status >= 400 AND status < 500 THEN 1 ELSE 0 END) as client_error,
       SUM(CASE WHEN status >= 500               THEN 1 ELSE 0 END) as server_error
FROM cloudfront_logs
WHERE timestamp BETWEEN ? AND ?
GROUP BY day
ORDER BY day
```

### API — `ApiController`
`GET /api/requests-per-day` returns `List<DailyStatusCount>`

### Frontend — `dashboard.js` `stackedBar()`
Three datasets read `d.success`, `d.clientError`, `d.serverError` with green / orange / red.

---

## `edge_response_result_type` values and colour mapping

| Value(s) | Meaning | Colour |
|---|---|---|
| `Hit` | Served from CloudFront cache | **Green** `rgba(40, 167, 69, 0.8)` |
| `Miss` | Forwarded to origin | **Blue** `rgba(54, 162, 235, 0.8)` |
| `FunctionGeneratedResponse` | Lambda@Edge / CF Function generated the response | **Orange** `rgba(253, 126, 20, 0.8)` |
| `FunctionExecutionError` | Function threw an exception | **Orange** `rgba(253, 126, 20, 0.8)` |
| `FunctionThrottledError` | Function was throttled | **Orange** `rgba(253, 126, 20, 0.8)` |
| `Error` | CloudFront or origin returned an error | **Red** `rgba(220, 53, 69, 0.8)` |
| `Redirect` | CloudFront issued a redirect (e.g. HTTP→HTTPS) | **Purple** `rgba(111, 66, 193, 0.8)` |

### Colour rationale for `Redirect`

Redirect is a deliberate, successful routing action — not a failure, not a cache event.
Purple (`rgba(111, 66, 193, 0.8)`, Bootstrap's `$purple`) is:
- Visually distinct from all four existing colours
- Neutral-to-positive in connotation (intentional, controlled)
- Already part of Bootstrap 5's colour palette so it fits the existing design language

The three Function values are grouped into a single **orange** dataset ("Function") because:
- They share a common origin (Lambda@Edge / CF Functions)
- Distinguishing them per day adds visual noise without actionable insight at this granularity
- Orange already carries the "attention needed" signal in the current chart

---

## Proposed new model — `DailyResultTypeCount.java`

```java
public record DailyResultTypeCount(
    LocalDate day,
    long hit,
    long miss,
    long function,   // FunctionGeneratedResponse + FunctionExecutionError + FunctionThrottledError
    long error,
    long redirect
) {}
```

---

## Proposed new SQL query

```sql
SELECT date(timestamp) as day,
       SUM(CASE WHEN edge_response_result_type = 'Hit'    THEN 1 ELSE 0 END) as hit,
       SUM(CASE WHEN edge_response_result_type = 'Miss'   THEN 1 ELSE 0 END) as miss,
       SUM(CASE WHEN edge_response_result_type IN (
               'FunctionGeneratedResponse',
               'FunctionExecutionError',
               'FunctionThrottledError')               THEN 1 ELSE 0 END) as function,
       SUM(CASE WHEN edge_response_result_type = 'Error'  THEN 1 ELSE 0 END) as error,
       SUM(CASE WHEN edge_response_result_type = 'Redirect' THEN 1 ELSE 0 END) as redirect
FROM cloudfront_logs
WHERE timestamp BETWEEN ? AND ?
GROUP BY day
ORDER BY day
```

---

## Files to change

| Action | File | What changes |
|--------|------|--------------|
| Replace | `src/main/java/.../model/DailyStatusCount.java` | New record `DailyResultTypeCount` with 5 count fields |
| Edit | `src/main/java/.../service/DashboardService.java` | New SQL, new return type |
| Edit | `src/main/java/.../web/ApiController.java` | Return type update |
| Edit | `src/main/resources/static/js/dashboard.js` | 5-dataset `stackedBar`, new colours, new field names |
| Edit | `src/test/java/.../service/DashboardServiceIntegrationTest.java` | Update/add assertions |
| Edit | `src/test/java/.../web/ApiControllerTest.java` | Update expected JSON shape |

The `DailyStatusCount` record is only used by `requestsPerDay()` and `stackedBar()` — no other
callers exist in the codebase — so the rename is safe and contained.

---

## Notes

- `edge_response_result_type` is already stored in the `edge_response_result_type` column
  (mapped from `x-edge-response-result-type`); **no migration needed**.
- The HTTP status columns (`status`) are retained — they are used elsewhere (e.g. `topAllowedUriStems` filters `status < 400`, `topBlockedCountries` filters `status = 403`).
- Stack order suggestion (bottom to top): Hit → Miss → Function → Redirect → Error — puts the
  most common positive results at the base and the attention-needed ones at the top.

---

## TODO

### Phase 1 — Model
- [x] Rename `DailyStatusCount.java` to `DailyResultTypeCount.java`
- [x] Replace fields `(success, clientError, serverError)` with `(hit, miss, function, error, redirect)`

### Phase 2 — Service
- [x] Replace the SQL in `DashboardService.requestsPerDay()` with the new `edge_response_result_type` CASE/SUM query
- [x] Update the return type from `DailyStatusCount` to `DailyResultTypeCount`
- [x] Update the `RowMapper` to read the new columns (`hit`, `miss`, `function`, `error`, `redirect`)

### Phase 3 — API
- [x] Update `ApiController.requestsPerDay()` return type to `List<DailyResultTypeCount>`

### Phase 4 — Frontend
- [x] Add `purple` to the `COLORS` map in `dashboard.js`
- [x] Replace the three-dataset `stackedBar()` call for `chartRequestsPerDay` with five datasets: Hit (green), Miss (blue), Function (orange), Redirect (purple), Error (red)
- [x] Update dataset field references from `d.success / d.clientError / d.serverError` to `d.hit / d.miss / d.function / d.redirect / d.error`
- [x] Update dataset labels to match the new semantics

### Phase 5 — Tests
- [x] Update `ApiControllerTest.requestsPerDayReturnsJson()`: mock returns `DailyResultTypeCount`, assert new JSON fields (e.g. `hit`, `miss`)
- [x] Update / extend `DashboardServiceIntegrationTest`: assert `requestsPerDay()` returns correct counts per result type
