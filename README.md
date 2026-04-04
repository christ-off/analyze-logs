# analyze-logs

CLI tool to fetch [Amazon CloudFront standard logs](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/AccessLogs.html) (JSON format) and store them in a local SQLite database for later analysis.

## Build

Requires Java 25 and Maven.

```bash
mvn package
```

Produces `target/analyze-logs-1.0-SNAPSHOT.jar` (fat jar, all dependencies included).

For convenience, create a wrapper script:

```bash
cat > analyze-logs << 'EOF'
#!/bin/sh
exec java --enable-native-access=ALL-UNNAMED \
  -jar "$(dirname "$0")/target/analyze-logs-1.0-SNAPSHOT.jar" "$@"
EOF
chmod +x analyze-logs
```

## Commands

### `fetch` — download logs from S3 into the local database

```
./analyze-logs fetch --bucket <bucket> [options]

Options:
  -b, --bucket       S3 bucket containing the CloudFront logs     (required)
  -p, --prefix       S3 key prefix for log files                  (default: root)
  -r, --region       AWS region                                   (default: us-east-1)
      --profile      AWS credentials profile name
  -d, --db           SQLite database file path                    (default: logs.db)
      --since        Only fetch files modified on or after date   (yyyy-MM-dd)
      --skip-existing  Skip files already imported                (default: true)
```

**Example — first run:**
```bash
./analyze-logs fetch \
  --bucket my-cloudfront-logs \
  --prefix AWSLogs/123456789/CloudFront/ \
  --region eu-west-3
```

**Example — incremental update (only new files since Jan 1):**
```bash
./analyze-logs fetch \
  --bucket my-cloudfront-logs \
  --prefix AWSLogs/123456789/CloudFront/ \
  --since 2025-01-01
```

**Example — use a named AWS profile:**
```bash
./analyze-logs fetch \
  --bucket my-cloudfront-logs \
  --profile my-aws-profile
```

Log files are decompressed automatically (`.gz` files are supported).

### `status` — show database statistics

```
./analyze-logs status [-d <db>]
```

```
Database      : logs.db
Entries       : 42381
Earliest      : 2024-06-01T00:03:12Z
Latest        : 2025-03-28T23:58:01Z
Distributions : 1
```

## AWS credentials

**No credentials are stored by this tool.** Authentication is delegated entirely to the [AWS SDK default credentials chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html), which resolves credentials in this order:

1. **Environment variables** — `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`
2. **System properties** — `aws.accessKeyId` and `aws.secretKey`
3. **`~/.aws/credentials`** file — populated by `aws configure`
4. **`~/.aws/config`** file — for SSO, role assumption, etc.
5. **Container/instance credentials** — ECS task role or EC2 instance profile

### Recommended setup

The simplest approach for a personal machine is to use the AWS CLI:

```bash
aws configure
# or, for a named profile:
aws configure --profile my-aws-profile
```

### Minimum required IAM permissions

The IAM user or role needs only read access to the log bucket:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:ListBucket",
        "s3:GetObject"
      ],
      "Resource": [
        "arn:aws:s3:::my-cloudfront-logs",
        "arn:aws:s3:::my-cloudfront-logs/*"
      ]
    }
  ]
}
```

## Database

Logs are stored in a SQLite file (`logs.db` by default). You can query it directly with any SQLite-compatible tool (DBeaver, DB Browser for SQLite, `sqlite3` CLI, datasette, etc.):

```bash
sqlite3 logs.db "SELECT method, status, COUNT(*) FROM cloudfront_logs GROUP BY method, status"
```

### Schema

```sql
cloudfront_logs (
    id                          INTEGER PRIMARY KEY,
    timestamp                   TEXT NOT NULL,      -- ISO-8601 UTC (date + time fields combined)
    edge_location               TEXT,               -- e.g. SFO53-P7
    sc_bytes                    INTEGER,            -- bytes sent server → viewer
    client_ip                   TEXT,
    method                      TEXT,               -- GET, POST, HEAD, …
    host                        TEXT,               -- CloudFront distribution domain
    uri_stem                    TEXT,               -- path only, no query string
    status                      INTEGER,            -- HTTP status code
    referer                     TEXT,               -- NULL when absent
    user_agent                  TEXT,               -- NULL when absent
    uri_query                   TEXT,               -- NULL when absent
    cookie                      TEXT,               -- NULL when absent or logging disabled
    edge_result_type            TEXT,               -- Hit / Miss / Error / Redirect / …
    x_host_header               TEXT,               -- alternate domain name (CNAME) if used
    protocol                    TEXT,               -- http / https / ws / wss / grpcs
    cs_bytes                    INTEGER,            -- bytes sent viewer → server
    time_taken                  REAL,               -- seconds (server perspective)
    x_forwarded_for             TEXT,               -- NULL if viewer connected directly
    ssl_protocol                TEXT,               -- NULL for plain HTTP
    ssl_cipher                  TEXT,               -- NULL for plain HTTP
    edge_response_result_type   TEXT,               -- Hit / Miss / Error / …
    protocol_version            TEXT,               -- HTTP/1.1 / HTTP/2.0 / HTTP/3.0
    fle_status                  TEXT,               -- NULL when field-level encryption not configured
    fle_encrypted_fields        INTEGER,            -- NULL when field-level encryption not configured
    client_port                 INTEGER,
    time_to_first_byte          REAL,               -- seconds
    edge_detailed_result_type   TEXT,               -- extended result type (OriginShieldHit, etc.)
    content_type                TEXT,               -- NULL when absent
    content_length              INTEGER,            -- NULL when absent
    range_start                 INTEGER,            -- NULL when no Content-Range header
    range_end                   INTEGER,            -- NULL when no Content-Range header
    country                     TEXT                -- ISO 3166-1 alpha-2
)

fetched_files (
    s3_key      TEXT PRIMARY KEY,   -- tracks which log files have been imported
    fetched_at  TEXT
)
```

### Example queries

```sql
-- Top requested paths
SELECT uri_stem, COUNT(*) AS hits
FROM cloudfront_logs
GROUP BY uri_stem
ORDER BY hits DESC
LIMIT 20;

-- Error rate by edge location
SELECT edge_location,
       COUNT(*) AS total,
       SUM(CASE WHEN status >= 400 THEN 1 ELSE 0 END) AS errors
FROM cloudfront_logs
GROUP BY edge_location;

-- Cache hit ratio per day
SELECT DATE(timestamp) AS day,
       ROUND(100.0 * SUM(CASE WHEN edge_result_type = 'Hit' THEN 1 ELSE 0 END) / COUNT(*), 1) AS hit_pct
FROM cloudfront_logs
GROUP BY day
ORDER BY day;

-- Top countries by bandwidth
SELECT country, SUM(sc_bytes) AS bytes
FROM cloudfront_logs
GROUP BY country
ORDER BY bytes DESC;
```