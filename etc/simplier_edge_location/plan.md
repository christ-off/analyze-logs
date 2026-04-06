# Implementation Plan: Human-Readable Edge Location

## Goal

Replace raw `edge_location` values like `CDG55-P2` with `Paris, France` in dashboard queries,
by resolving the IATA prefix against a bundled AWS edge locations dataset.

- The **IATA code** (e.g. `CDG`) is stored in the database — stable, compact, and reusable.
- **Translation to human-readable** (city, country, pricingRegion) happens in the service layer,
  not at insert time. This keeps the DB neutral and lets display logic evolve without migrations.

---

## Step 1 — Bundle the lookup data

Download the JSON and place it as a static classpath resource:

```
src/main/resources/aws-edge-locations.json
```

The file is ~50 KB and static — no runtime HTTP call needed.

---

## Step 2 — Create `EdgeLocationResolver`

New class: `src/main/java/com/example/analyzelog/service/EdgeLocationResolver.java`

Mirrors the `UserAgentClassifier` pattern: loaded once at startup, pure lookup at call time.

The `Location` record maps all fields useful for future dashboard facets, including `pricingRegion`.

```java
package com.example.analyzelog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class EdgeLocationResolver {

    private static final Pattern IATA = Pattern.compile("^([A-Z]{3})\\d+(?:-P\\d+)?$");

    public record Location(String city, String country, String countryCode, String pricingRegion) {
        public String display() { return city + ", " + country; }
    }

    private final Map<String, Location> locations;

    public EdgeLocationResolver() {
        this.locations = load();
    }

    /** Extracts the IATA code from a raw edge location value. Returns null if unrecognised. */
    public String extractIata(String raw) {
        if (raw == null || raw.isBlank()) return null;
        var m = IATA.matcher(raw);
        return m.matches() ? m.group(1) : null;
    }

    /** Resolves a stored IATA code to its Location. Returns null if not found. */
    public Location resolve(String iata) {
        if (iata == null) return null;
        return locations.get(iata);
    }

    /** Convenience: resolves a raw edge location value directly to a display string. */
    public String resolveDisplay(String iata) {
        var loc = resolve(iata);
        return loc != null ? loc.display() : iata;
    }

    private static Map<String, Location> load() {
        try {
            var mapper = new ObjectMapper();
            var root = mapper.readTree(new ClassPathResource("aws-edge-locations.json").getInputStream());
            var map = new HashMap<String, Location>();
            root.fields().forEachRemaining(e -> {
                JsonNode n = e.getValue();
                map.put(e.getKey(), new Location(
                        n.path("city").asText(),
                        n.path("country").asText(),
                        n.path("countryCode").asText(),
                        n.path("pricingRegion").asText(null)
                ));
            });
            return Map.copyOf(map);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to load aws-edge-locations.json", ex);
        }
    }
}
```

---

## Step 3 — Add `edge_location_iata` column (Liquibase)

New file: `src/main/resources/db/changelog/changes/003-add-edge-location-iata.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
            https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <changeSet id="003-add-edge-location-iata" author="christophe">
        <addColumn tableName="cloudfront_logs">
            <column name="edge_location_iata" type="TEXT"/>
        </addColumn>
        <createIndex tableName="cloudfront_logs" indexName="idx_cloudfront_logs_edge_location_iata">
            <column name="edge_location_iata"/>
        </createIndex>
    </changeSet>

</databaseChangeLog>
```

Register it in `db.changelog-master.xml`:

```xml
<include file="db/changelog/changes/003-add-edge-location-iata.xml"/>
```

Storing only the IATA code keeps the DB free of display concerns. If the lookup dataset is
updated (e.g. a city name changes), no migration or data backfill is needed.

---

## Step 4 — Wire `EdgeLocationResolver` into `LogRepository`

`LogRepository` uses the resolver only to **extract** the IATA code from the raw value — it
does not do any display translation. Same injection pattern as `UserAgentClassifier`.

```java
private final EdgeLocationResolver edgeLocationResolver;

public LogRepository(JdbcTemplate jdbc,
                     UserAgentClassifier classifier,
                     EdgeLocationResolver edgeLocationResolver) {
    this.jdbc = jdbc;
    this.classifier = classifier;
    this.edgeLocationResolver = edgeLocationResolver;
}
```

In `saveEntries()`, extend the INSERT to include the new column (column 22):

```java
jdbc.batchUpdate("""
        INSERT INTO cloudfront_logs (
            timestamp, edge_location, sc_bytes, client_ip, method,
            uri_stem, status, referer, user_agent,
            edge_result_type, protocol, cs_bytes, time_taken,
            edge_response_result_type, protocol_version, time_to_first_byte,
            edge_detailed_result_type, content_type, content_length, country,
            ua_name, edge_location_iata
        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        entries, entries.size(),
        (stmt, e) -> {
            // ... existing bindings 1–21 unchanged ...
            stmt.setString(22, edgeLocationResolver.extractIata(e.edgeLocation()));
        });
```

---

## Step 5 — Add `topEdgeLocations` to `DashboardService`

`DashboardService` injects `EdgeLocationResolver` and translates IATA codes to display strings
when building the result list. The SQL groups on the compact `edge_location_iata` column.

```java
private final EdgeLocationResolver edgeLocationResolver;

public DashboardService(JdbcTemplate jdbc, EdgeLocationResolver edgeLocationResolver) {
    this.jdbc = jdbc;
    this.edgeLocationResolver = edgeLocationResolver;
}

public List<NameCount> topEdgeLocations(Instant from, Instant to, int limit) {
    return jdbc.query("""
            SELECT edge_location_iata as iata, COUNT(*) as count
            FROM cloudfront_logs
            WHERE timestamp BETWEEN ? AND ?
              AND edge_location_iata IS NOT NULL
            GROUP BY edge_location_iata
            ORDER BY count DESC
            LIMIT ?
            """,
            (rs, _) -> new NameCount(
                    edgeLocationResolver.resolveDisplay(rs.getString("iata")),
                    rs.getLong("count")),
            from.toString(), to.toString(), limit);
}
```

---

## Step 6 — Expose via `ApiController`

```java
@GetMapping("/api/edge-locations")
public List<NameCount> edgeLocations(
        @RequestParam Instant from,
        @RequestParam Instant to,
        @RequestParam(defaultValue = "10") int limit) {
    return dashboardService.topEdgeLocations(from, to, limit);
}
```

---

## File change summary

| Action | File |
|--------|------|
| Add resource | `src/main/resources/aws-edge-locations.json` |
| Create | `src/main/java/.../service/EdgeLocationResolver.java` |
| Create | `src/main/resources/db/changelog/changes/003-add-edge-location-iata.xml` |
| Edit   | `src/main/resources/db/changelog/db.changelog-master.xml` |
| Edit   | `src/main/java/.../repository/LogRepository.java` |
| Edit   | `src/main/java/.../service/DashboardService.java` |
| Edit   | `src/main/java/.../web/ApiController.java` |

---

## TODO

### Phase 1 — Data
- [x] Download `aws-edge-locations.json` and save to `src/main/resources/aws-edge-locations.json`

### Phase 2 — Resolver
- [x] Create `EdgeLocationResolver` in `com.example.analyzelog.service`
- [x] Implement `extractIata(String raw)` with the IATA regex
- [x] Implement `resolve(String iata)` map lookup
- [x] Implement `resolveDisplay(String iata)` convenience method
- [x] Register as a `@Bean` in `AppConfig` (or keep `@Component`)
- [x] Write unit tests for `extractIata` (valid codes, unknown codes, null/blank)
- [x] Write unit tests for `resolveDisplay` (known IATA, unknown IATA, null)

### Phase 3 — Database
- [x] Create `003-add-edge-location-iata.xml` Liquibase changeset
- [x] Register it in `db.changelog-master.xml`

### Phase 4 — Persistence
- [x] Inject `EdgeLocationResolver` into `LogRepository`
- [x] Add `edge_location_iata` column to the INSERT statement in `saveEntries()`
- [x] Bind `edgeLocationResolver.extractIata(e.edgeLocation())` as parameter 22

### Phase 5 — Service
- [x] Inject `EdgeLocationResolver` into `DashboardService`
- [x] Implement `topEdgeLocations(Instant from, Instant to, int limit)`
- [x] Write integration test for `topEdgeLocations`

### Phase 6 — API
- [x] Add `GET /api/edge-locations` endpoint to `ApiController`

---

## Notes

- The raw `edge_location` column is kept intact — `edge_location_iata` is additive.
- No backfill needed — database will be wiped and all log files reloaded from scratch.
- `pricingRegion` is mapped in `Location` for future use (e.g. a pricing-region breakdown chart)
  without requiring any schema change.
- The `-Px` suffix and rack number are treated as noise and stripped (see `research.md`).