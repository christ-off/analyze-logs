# Research — Issue #2: User Agent Groups Pie Chart

## Goal
Add a pie chart on the main dashboard (row 2, first slot) that groups `ua_name` values into configured categories and shows relative traffic share per group.

---

## Data analysis

From the sample data in the issue (total ≈ 10,100 requests):

| Group (proposed) | UA labels | Count |
|---|---|---|
| AI Bots | ClaudeBot, Claude-SearchBot, OAI-SearchBot, PerplexityBot | ~1,837 |
| Browsers | Chrome/\*, Firefox/\*, Safari/\*, Edge/\*, HeadlessChrome | ~4,049 |
| Fediverse | Mastodon, Misskey | ~755 |
| Feed Readers | Feedbin, Feedly, Reeder, FreshRSS, NewsBlur, rss-parser, sfFeedReader, Feeder, Flus, WP.com FeedBot | ~632 |
| Other Bots | AhrefsBot, SemrushBot, SERankingBot, Barkrowler, DotBot, MJ12bot, HeadlineBot, PetalBot, CocCocBot, SeznamBot, SummalyBot, Yumechi Proxy, WellKnownBot, Censys Scanner, Palo Alto Scanner, VisionHeight Scanner, 2ip Scanner, HeadlessChrome | ~870 |
| Search Bots | Googlebot, Google ImageProxy, Google FeedFetcher, Bingbot, YandexBot, Baiduspider, DuckDuckBot, Qwantbot | ~380 |
| Apps | Obsidian, Google Search App, okhttp, Go HTTP client | ~486 |
| Other | (no user agent), Unknown, Facebook, unmatched | ~(remaining) |

### Suggested additional groups vs. issue proposal
- **AI Bots** is a significant group (~18%) worth separating from "Other Bots"
- **Security Scanners** merged into "Other Bots" (too small at <1% to warrant own slice)
- **Apps** (Obsidian 459 etc. = ~486, Facebook excluded) is sizable and meaningful — worth its own group
- **Final groups**: AI Bots, Browsers, Fediverse, Feed Readers, Search Bots, Other Bots, Apps, Other

### Browsers use prefixes, not fixed labels
`UserAgentClassifier` produces dynamic labels: `"Chrome / Windows"`, `"Firefox / Linux"`, etc.  
The group config needs to support **prefix matching** (e.g. `"Chrome / "`) in addition to exact label matching.

---

## Architecture

### Config model — new `UaGroupProperties`
```java
@ConfigurationProperties(prefix = "ua-groups")
public record UaGroupProperties(List<Group> groups) {
    public record Group(String name, List<String> labels, List<String> labelPrefixes) {}
}
```
- `labels`: exact match against `ua_name` (e.g. `"ClaudeBot"`)
- `labelPrefixes`: prefix match (e.g. `"Chrome / "` catches all Chrome/OS variants)
- First matching group wins; labels not matched by any group → **"Other"**

### Service method — `DashboardService.uaGroupCounts(from, to)`
1. `SELECT ua_name, COUNT(*) as count FROM cloudfront_logs WHERE timestamp BETWEEN ? AND ? GROUP BY ua_name`
2. In Java: iterate groups in order, match each `ua_name` by exact label or prefix
3. Sum counts per group; remainder → "Other"
4. Returns `List<NameCount>` sorted by count DESC

No new SQL constant needed — straightforward query.

### API endpoint — `GET /api/ua-groups`
In `ApiController`:
```java
@GetMapping("/ua-groups")
public List<NameCount> uaGroups(@RequestParam String from, @RequestParam String to) { ... }
```
Returns `List<NameCount>` — same type already used for referers, edge locations.

### Frontend

**`dashboard.html`** — row 2 changes from full-width `Top URLs` to two `col-xl-6` cards:
```
Row 2: [UA Groups pie (col-xl-6)] [Top URLs stacked bar (col-xl-6)]
```

**`dashboard.js`** — add one `loadChart` call:
```js
Charts.loadChart(`ua-groups?${p}`, data => Charts.pie('chartUaGroups', data, null));
```
Uses the existing `Charts.pie` with `Charts.PALETTE` (no fixed color map needed since group names are configurable).

---

## `application.yml` addition

```yaml
ua-groups:
  groups:
    - name: "AI Bots"
      labels: ["ClaudeBot", "Claude-SearchBot", "OAI-SearchBot", "PerplexityBot"]
    - name: "Browsers"
      labels: ["Unknown"]
      labelPrefixes: ["Chrome / ", "Firefox / ", "Safari / ", "Edge / "]
    - name: "Fediverse"
      labels: ["Mastodon", "Misskey"]
    - name: "Feed Readers"
      labels: ["Feedly", "Feedbin", "Reeder", "FreshRSS", "NewsBlur",
               "rss-parser", "sfFeedReader", "Feeder", "Flus", "WP.com FeedBot"]
    - name: "Search Bots"
      labels: ["Googlebot", "Google ImageProxy", "Google FeedFetcher",
               "Bingbot", "YandexBot", "Baiduspider", "DuckDuckBot", "Qwantbot"]
    - name: "Other Bots"
      labels: ["AhrefsBot", "SemrushBot", "SERankingBot", "Barkrowler", "DotBot",
               "MJ12bot", "HeadlineBot", "PetalBot", "CocCocBot", "SeznamBot",
               "SummalyBot", "Yumechi Proxy",
               "WellKnownBot", "Censys Scanner", "Palo Alto Scanner",
               "VisionHeight Scanner", "2ip Scanner", "HeadlessChrome"]
    - name: "Apps"
      labels: ["Obsidian", "Google Search App", "okhttp", "Go HTTP client"]
    # Remaining ((no user agent), Facebook, truly unknown) fall into "Other"
```
---

## Impacted files

| File | Change |
|---|---|
| `UaGroupProperties.java` | **New** — `@ConfigurationProperties` record |
| `AppConfig.java` | Register `UaGroupProperties` bean (or via `@EnableConfigurationProperties`) |
| `DashboardService.java` | Add `uaGroupCounts(from, to)` |
| `ApiController.java` | Add `GET /api/ua-groups` |
| `dashboard.html` | Row 2: split into 2×`col-xl-6` (pie + Top URLs) |
| `dashboard.js` | Add `loadChart` for `ua-groups`, render with `Charts.pie` |
| `application.yml` | Add `ua-groups` section |
| `application-local.yml` | No change needed (inherits from `application.yml`) |
| `ApiControllerTest.java` | Add test for `/api/ua-groups` |
| `DashboardServiceIntegrationTest.java` | Add test for `uaGroupCounts` |
| New `UaGroupServiceTest.java` | Unit test for grouping logic (exact + prefix matching, "Other" bucket) |

---

## Key design decisions

1. **Java-side grouping** (not SQL CASE): group list is configurable at runtime, SQL CASE would require code changes per group change.
2. **`labelPrefixes`** field handles browsers cleanly without enumerating all OS variants.
3. **`(no user agent)` goes to "Other"** — it is a distinct bucket in `ua_name` but has no group.
4. **Order in config = priority**: first matching group wins, consistent with `UaClassifierProperties`.
5. **`Charts.PALETTE`** for colors — group names are user-defined so fixed color map would be brittle.
6. **No new model record needed** — `NameCount(name, count)` is sufficient for the pie.