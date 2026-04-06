# Research & Plan: Top User Agents — Stacked by Result Type

## Goal

Turn the "Top User Agents" horizontal bar chart into a **horizontal stacked bar**, where each
bar is split into Hit / Miss / Function / Redirect / Error segments — exactly the same colour
scheme used in "Requests per Day".

---

## Current state

### API `GET /api/ua-names` → `List<NameCount>`
```java
SELECT ua_name as name, COUNT(*) as count
FROM cloudfront_logs
WHERE timestamp BETWEEN ? AND ?
GROUP BY ua_name ORDER BY count DESC LIMIT ?
```

### Chart — `horizontalBar(canvasId, data)` in `dashboard.js`
One blue bar per UA, total count only. Legend hidden.

---

## Target state

### API `GET /api/ua-names-split` → `List<NameResultTypeCount>`
Same grouping key (`ua_name`) but five counts instead of one.

### Chart — new `horizontalStackedBar(canvasId, data)` in `dashboard.js`
Horizontal stacked bar, 5 colour-coded segments per UA bar.

---

## Code reuse opportunities

### 1. SQL CASE/SUM block — extract as a constant

The five `SUM(CASE ...)` expressions in `requestsPerDay()` are identical to what the new
query needs. Extract them as a `private static final String` in `DashboardService`:

```java
private static final String RESULT_TYPE_SUMS = """
        SUM(CASE WHEN edge_response_result_type = 'Hit'    THEN 1 ELSE 0 END) as hit,
        SUM(CASE WHEN edge_response_result_type = 'Miss'   THEN 1 ELSE 0 END) as miss,
        SUM(CASE WHEN edge_response_result_type IN (
                'FunctionGeneratedResponse',
                'FunctionExecutionError',
                'FunctionThrottledError')                  THEN 1 ELSE 0 END) as function,
        SUM(CASE WHEN edge_response_result_type = 'Error'    THEN 1 ELSE 0 END) as error,
        SUM(CASE WHEN edge_response_result_type = 'Redirect' THEN 1 ELSE 0 END) as redirect\
""";
```

Both `requestsPerDay()` and the new `topUserAgentsByResultType()` embed it verbatim.

### 2. RowMapper for the five counts — extract as a static method

```java
private static long[] resultTypeCounts(ResultSet rs) throws SQLException {
    return new long[]{
        rs.getLong("hit"),
        rs.getLong("miss"),
        rs.getLong("function"),
        rs.getLong("error"),
        rs.getLong("redirect")
    };
}
```

Used by both row mappers to avoid repeating the five `rs.getLong(...)` calls.

### 3. New model `NameResultTypeCount`

`DailyResultTypeCount` uses `LocalDate day` as its grouping key. The new record uses
`String name`. They cannot share a single record without losing type-safety, so introduce:

```java
// src/main/java/com/example/analyzelog/model/NameResultTypeCount.java
public record NameResultTypeCount(
    String name,
    long hit, long miss, long function, long error, long redirect
) {}
```

`DailyResultTypeCount` is left unchanged — its `LocalDate day` field is semantically distinct.

### 4. JavaScript — extract `resultTypeDatasets` helper

Both `stackedBar` (vertical, keyed by `d.day`) and the new `horizontalStackedBar` (horizontal,
keyed by `d.name`) share the same five dataset definitions. Extract:

```js
function resultTypeDatasets(data) {
    return [
        { label: 'Hit',      data: data.map(d => d.hit),      backgroundColor: COLORS.green  },
        { label: 'Miss',     data: data.map(d => d.miss),     backgroundColor: COLORS.blue   },
        { label: 'Function', data: data.map(d => d.function), backgroundColor: COLORS.orange },
        { label: 'Redirect', data: data.map(d => d.redirect), backgroundColor: COLORS.purple },
        { label: 'Error',    data: data.map(d => d.error),    backgroundColor: COLORS.red    },
    ];
}
```

Refactor `stackedBar` to use it:

```js
function stackedBar(canvasId, data) {
    const ctx = document.getElementById(canvasId);
    if (!ctx) return;
    new Chart(ctx, {
        type: 'bar',
        data: {
            labels: data.map(d => d.day),
            datasets: resultTypeDatasets(data),
        },
        options: {
            responsive: true,
            scales: { x: { stacked: true }, y: { stacked: true, beginAtZero: true } }
        }
    });
}
```

Add the new chart function:

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
            scales: { x: { stacked: true, beginAtZero: true }, y: { stacked: true } }
        }
    });
}
```

Wire it up:

```js
loadChart('ua-names-split', data => horizontalStackedBar('chartUaNames', data));
```

The old `GET /api/ua-names` endpoint and `topUserAgents()` service method can be removed once
nothing else references them — confirmed: `ApiController` is the only caller.

---

## New service method

```java
public List<NameResultTypeCount> topUserAgentsByResultType(Instant from, Instant to, int limit) {
    return jdbc.query("""
            SELECT ua_name as name,
            """ + RESULT_TYPE_SUMS + """
            FROM cloudfront_logs
            WHERE timestamp BETWEEN ? AND ?
            GROUP BY ua_name
            ORDER BY (hit + miss + function + error + redirect) DESC
            LIMIT ?
            """,
            (rs, _) -> new NameResultTypeCount(
                    rs.getString("name"),
                    rs.getLong("hit"),
                    rs.getLong("miss"),
                    rs.getLong("function"),
                    rs.getLong("error"),
                    rs.getLong("redirect")),
            from.toString(), to.toString(), limit);
}
```

Note: `ORDER BY (hit + miss + ...)` replaces `ORDER BY count DESC` since there is no longer a
single `count` column. Ordering by total keeps the chart sorted by most-active agent.

---

## Files to change

| Action  | File | What changes |
|---------|------|--------------|
| Create  | `src/main/java/.../model/NameResultTypeCount.java` | New record |
| Edit    | `src/main/java/.../service/DashboardService.java` | Extract `RESULT_TYPE_SUMS`, refactor `requestsPerDay`, add `topUserAgentsByResultType`, remove `topUserAgents` |
| Edit    | `src/main/java/.../web/ApiController.java` | Replace `/api/ua-names` with `/api/ua-names-split`, update return type |
| Edit    | `src/main/resources/static/js/dashboard.js` | Extract `resultTypeDatasets`, refactor `stackedBar`, add `horizontalStackedBar`, update `loadChart` call |
| Edit    | `src/test/java/.../web/ApiControllerTest.java` | Update `uaNamesReturnsJson` test |
| Edit    | `src/test/java/.../service/DashboardServiceIntegrationTest.java` | Replace/add test for new method |

---

## TODO

### Phase 1 — Model
- [x] Create `NameResultTypeCount(String name, long hit, long miss, long function, long error, long redirect)`

### Phase 2 — Service
- [x] Extract `RESULT_TYPE_SUMS` SQL constant from `requestsPerDay()`
- [x] Refactor `requestsPerDay()` to use the constant
- [x] Add `topUserAgentsByResultType(from, to, limit)` using the constant
- [x] Remove `topUserAgents()` (no remaining callers after API update)

### Phase 3 — API
- [x] Replace `GET /api/ua-names` → `GET /api/ua-names-split` returning `List<NameResultTypeCount>`
- [x] Remove unused import of `NameCount` if no longer needed

### Phase 4 — Frontend
- [x] Extract `resultTypeDatasets(data)` helper in `dashboard.js`
- [x] Refactor `stackedBar()` to use `resultTypeDatasets`
- [x] Add `horizontalStackedBar(canvasId, data)` using `resultTypeDatasets`
- [x] Update `loadChart` call: `ua-names-split` → `horizontalStackedBar('chartUaNames', data)`

### Phase 5 — Tests
- [x] Update `ApiControllerTest.uaNamesReturnsJson()`: mock `topUserAgentsByResultType`, assert `hit` field in JSON
- [x] Replace `topUserAgents` integration test with one for `topUserAgentsByResultType`

---

## Conclusion

All 5 phases implemented. 111 tests, 0 failures.

**No dead code introduced or left behind** — verified after completion:
- `horizontalBar()` remains active (Countries and URI Stems charts)
- `NameCount` remains active (Countries, URI Stems, Edge Locations endpoints)
- `COUNT_FIELD` constant remains active (3 queries in `DashboardService`)

**Reuse achieved:**
- `RESULT_TYPE_SUMS` SQL constant is now shared between `requestsPerDay()` and
  `topUserAgentsByResultType()` — the five `SUM(CASE ...)` expressions are defined once.
- `resultTypeDatasets()` JS helper is shared between `stackedBar()` and
  `horizontalStackedBar()` — the five dataset definitions (labels + colours) are defined once.

**Integration test note:** UA strings passed to the factory method must be real browser UA
strings, not display labels, because `ua_name` is populated by `UserAgentClassifier` at insert
time — not stored verbatim from the log entry.
