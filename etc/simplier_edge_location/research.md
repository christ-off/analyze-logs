# Edge Location Simplification Research

## Problem

AWS edge location values in S3 access logs look like `SFO53-P7`, `CDG55-P2`, `HKG54-P1`.

Structure:
```
SFO   53   -P7
 │     │    │
 │     │    └── PoP index (internal, undocumented) → noise
 │     └──────── server/rack number        → noise
 └────────────── IATA airport code         → meaningful
```

Only the 3-letter IATA prefix carries human-readable meaning.

## Extraction Rule

Regex: `^([A-Z]{3})\d+(-P\d+)?$`

Capture group 1 → IATA code.

## Lookup Source

**URL:** `https://raw.githubusercontent.com/tobilg/aws-edge-locations/refs/heads/main/data/aws-edge-locations.json`

Top-level keys are IATA codes. Each entry contains:

| Field         | Example            |
|---------------|--------------------|
| `city`        | `"San Francisco"`  |
| `country`     | `"United States"`  |
| `countryCode` | `"US"`             |
| `latitude`    | `37.619`           |
| `longitude`   | `-122.375`         |
| `pricingRegion` | `"United States, Mexico, & Canada"` |

## Sample Mappings (from the log extract)

| Raw value   | IATA | Resolved label              |
|-------------|------|-----------------------------|
| `ORD56-P13` | ORD  | Chicago, United States      |
| `SFO53-P7`  | SFO  | San Francisco, United States|
| `CDG55-P2`  | CDG  | Paris, France               |
| `IAD55-P6`  | IAD  | Washington, United States   |
| `FRA60-P3`  | FRA  | Frankfurt am Main, Germany  |
| `LHR5-P1`   | LHR  | London, Great Britain       |
| `HIO52-P2`  | HIO  | Portland, United States     |
| `ARN56-P1`  | ARN  | Stockholm, Sweden           |
| `ATL59-P10` | ATL  | Atlanta, United States      |
| `LAX54-P1`  | LAX  | Los Angeles, United States  |
| `HKG54-P1`  | HKG  | Hong Kong, China            |

## Recommended Display Format

`{city}, {country}` — concise and unambiguous for charts/tables.

Optionally `{city} ({countryCode})` for compact display: `Paris (FR)`.

## Implementation Approach

1. **Bundle the JSON** as a resource file (`src/main/resources/aws-edge-locations.json`) — the file is static and small enough (~50 KB) to ship with the app. Avoids a runtime HTTP call.
2. **At startup**, load it into a `Map<String, EdgeLocation>` (record with city, country, countryCode).
3. **On each log entry**, apply the regex to extract the IATA code, then do a map lookup. Fall back to the raw value if not found.

```java
private static final Pattern EDGE_CODE = Pattern.compile("^([A-Z]{3})\\d+(?:-P\\d+)?$");

public String resolve(String raw) {
    var m = EDGE_CODE.matcher(raw);
    if (!m.matches()) return raw;
    var loc = locations.get(m.group(1));
    return loc != null ? loc.city() + ", " + loc.country() : raw;
}
```

## Alternative: country-only grouping

For the "top countries" chart the IATA → `countryCode` mapping is sufficient — no city needed.

## Note: the `-Px` suffix is not a pricing tier

**Finding:** AWS does not publicly document the `-Px` suffix. It is **not** a pricing tier ordinal — `P10` is not "higher" or "more expensive" than `P1`.

Current understanding:
- Multiple PoPs (Points of Presence) can exist near the same airport (different data centers in the same metro area).
- The number after `-P` is an internal AWS sequence index for those PoPs, with no guaranteed ordering or public meaning.
- It is safe to strip it along with the rack number.

**Actual pricing classification** is carried by the `pricingRegion` field in the edge locations JSON (e.g. `"Europe"`, `"United States, Mexico, & Canada"`), derivable from the IATA code lookup — not from the `-Px` suffix.

Sources checked: AWS S3 access log format docs, CloudFront pricing page, `tobilg/aws-edge-locations` dataset — none document the `-Px` suffix.
