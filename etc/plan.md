# Plan: Robots.txt Disobedient Bots

Feature adds a section to the bot-analysis page showing disallowed bots that ignore `robots.txt`.

---

## Step 1 — Liquibase changeset

Create `src/main/resources/db/changelog/changes/011-robots-disallowed.xml`.

```sql
CREATE TABLE robots_disallowed (
    user_agent   TEXT NOT NULL PRIMARY KEY,
    refreshed_at TEXT NOT NULL
);
```

Register it in `db/changelog/db.changelog-master.xml`.

---

## Step 2 — Configuration

In `AppProperties`, add field `String robotsUrl`.

In `application.yml`, add default:
```yaml
app:
  robots-url: "https://post-tenebras-lire.net/robots.txt"
```

---

## Step 3 — Model

New record `src/main/java/com/example/analyzelog/model/DisobedientBot.java`:

```java
public record DisobedientBot(String uaName, long count, long hit, long miss, long error, long filtered) {}
```

---

## Step 4 — RobotsService

New class `src/main/java/com/example/analyzelog/service/RobotsService.java`.

**`refresh()`** — fetches `appProperties.robotsUrl()`, parses RFC 9309 format:
- Group lines by `User-agent:` blocks (skip `*`)
- Collect blocks that have at least one `Disallow:` entry
- Upsert each UA name into `robots_disallowed` with current timestamp

**`findDisobedientBots(Instant from, Instant to)`** — returns `List<DisobedientBot>`:

```sql
SELECT c.ua_name,
       COUNT(*) AS count,
       SUM(CASE WHEN c.edge_result_type = 'Hit'     THEN 1 ELSE 0 END) AS hit,
       SUM(CASE WHEN c.edge_result_type = 'Miss'    THEN 1 ELSE 0 END) AS miss,
       SUM(CASE WHEN c.edge_result_type LIKE '%Error%'  THEN 1 ELSE 0 END) AS error,
       SUM(CASE WHEN c.edge_result_type LIKE '%Filter%' THEN 1 ELSE 0 END) AS filtered
FROM cloudfront_logs c
INNER JOIN robots_disallowed r ON c.ua_name = r.user_agent
WHERE c.uri_stem != '/robots.txt'
  AND c.timestamp BETWEEN ? AND ?
GROUP BY c.ua_name
ORDER BY count DESC
```

**`getRefreshedAt()`** — returns the most recent `refreshed_at` from the table (or empty).

---

## Step 5 — API endpoints

In `ApiController`, add two endpoints under `/api`:

```
GET /api/robots-disobedient?from=&to=   → List<DisobedientBot>
GET /api/robots-refresh                  → plain text "OK" or error message
```

`robots-refresh` calls `robotsService.refresh()` and returns a status string. Follows the same GET-action pattern as the existing S3 refresh (no CSRF concern per project security model).

---

## Step 6 — bot-analysis.html

Add a new card at the bottom of `.page-content`, after the Top Bot IPs card:

```html
<div class="row g-4 mb-4">
  <div class="col-12">
    <div class="card">
      <div class="card-header d-flex justify-content-between align-items-center fw-semibold">
        <span>Robots.txt Disobedient Bots</span>
        <span id="robotsRefreshedAt" class="text-muted small me-auto ms-3"></span>
        <button id="refreshRobotsBtn" class="btn btn-sm btn-outline-warning">Refresh Robots</button>
      </div>
      <div class="card-body p-0">
        <table class="table table-sm table-hover mb-0">
          <thead class="table-dark">
            <tr>
              <th>User Agent</th>
              <th class="text-end">Requests</th>
              <th style="min-width:200px">Hit / Miss / Error / Filtered</th>
            </tr>
          </thead>
          <tbody id="disobedientBotsTable">
            <tr><td colspan="3" class="text-center text-muted py-3">Loading...</td></tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</div>
```

---

## Step 7 — bot-analysis.js

**`loadDisobedientBots(data)`** — populates `#disobedientBotsTable`.

Each row renders a mini proportional bar as inline `<div>` segments using Bootstrap bg colors:
- `bg-success` → Hit
- `bg-warning` → Miss
- `bg-danger` → Error
- `bg-secondary` → Filtered

```js
function resultBar({ hit, miss, error, filtered }) {
    const total = hit + miss + error + filtered || 1;
    const seg = (val, cls) =>
        val > 0 ? `<div class="${cls}" style="width:${(val/total*100).toFixed(1)}%;height:16px;display:inline-block" title="${cls.split('-')[1]}: ${val}"></div>` : '';
    return `<div class="d-flex" style="background:#dee2e6;border-radius:3px;overflow:hidden">
        ${seg(hit,'bg-success')}${seg(miss,'bg-warning')}${seg(error,'bg-danger')}${seg(filtered,'bg-secondary')}
    </div>`;
}
```

**Refresh button** wires to `GET /api/robots-refresh`, shows a spinner on the button while pending, then reloads the table and updates `#robotsRefreshedAt`.

Add `loadDisobedientBots` call inside `loadAllCharts()`.

---

## Step 8 — Tests

**`RobotsServiceTest.java`** (unit, in-memory H2 or SQLite):
- `refresh_parsesDisallowedAgents` — feeds a mock robots.txt string, verifies DB rows
- `refresh_skipsWildcard` — ensures `*` is not stored
- `findDisobedientBots_returnsOnlyNonRobotsTxtRequests` — verifies uri_stem filter
- `findDisobedientBots_emptyWhenNoData` — returns empty list gracefully

**`RobotsServiceParsingTest.java`** (pure unit, no DB):
- Various robots.txt edge cases: multiple UAs per block, blank lines, comments, `Allow:` lines

**`bot-analysis.test.js`** (Vitest):
- `loadDisobedientBots` renders rows correctly
- `loadDisobedientBots` shows empty-state message when array is empty
- Refresh button calls `/api/robots-refresh` and reloads table

---

## Step 9 — README

Update the Schema section to document `robots_disallowed`. Update the Configuration table to document `app.robots-url`.

---

## Commit

Follow git-commit-push skill. Suggested message:

```
feat(bot-analysis): show robots.txt disobedient bots

Add robots_disallowed table, RobotsService (fetch + parse + query),
/api/robots-disobedient and /api/robots-refresh endpoints, and a new
card on the bot-analysis page with per-UA request count and a
Hit/Miss/Error/Filtered proportional bar.
```
