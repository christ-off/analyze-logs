# analyze-logs

Spring Boot web dashboard for [Amazon CloudFront standard logs](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/AccessLogs.html) (JSON format).
Fetches log files from S3, stores them in a local SQLite database, and displays four interactive charts.

## Build & run

Requires Java 25 and Maven.

```bash
mvn package
java -jar target/analyze-logs-1.0-SNAPSHOT.jar --spring.profiles.active=local
```

Or without packaging:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Open `http://localhost:8080`.

## Configuration

### `application.yml` (committed — safe defaults)

Key properties:

| Key | Default | Description |
|-----|---------|-------------|
| `app.aws.region` | `us-east-1` | AWS region of the S3 bucket |
| `app.aws.bucket` | `` | S3 bucket containing CloudFront logs |
| `app.aws.prefix` | `` | S3 key prefix (e.g. `AWSLogs/123456789/CloudFront/`) |
| `app.aws.profile` | `` | AWS credentials profile (`~/.aws/credentials`); empty = default chain |
| `app.db-path` | `logs.db` | SQLite file path (relative to working directory) |
| `server.port` | `8080` | HTTP port |

### `application-local.yml` (gitignored — your secrets)

Create `src/main/resources/application-local.yml` to override values without touching the committed file:

```yaml
app:
  aws:
    bucket: "my-cloudfront-logs"
    prefix: "AWSLogs/123456789/CloudFront/"
    profile: "my-aws-profile"
  db-path: /absolute/path/to/logs.db
```

This file is listed in `.gitignore` and will never be committed. Activate it with `--spring.profiles.active=local`.

### User-agent classification rules

The `ua-classifier.rules` list in `application.yml` maps UA substrings to human-readable labels.
Rules are evaluated top-to-bottom; first match wins. Add entries to classify custom bots or internal tools:

```yaml
ua-classifier:
  rules:
    - pattern: "MyInternalBot"
      label: "Internal crawler"
```

## Dashboard

Four charts, all scoped to the selected date range:

| Chart | Description |
|-------|-------------|
| Top User Agents | Horizontal bar — classified UA names by request count |
| Top Blocked Countries | Horizontal bar — countries returning 403 |
| Top Allowed URLs | Horizontal bar — most-requested paths (status < 400) |
| Requests per Day | Stacked bar — success (2xx/3xx) / client error (4xx) / server error (5xx) |

Date range presets: **Today / 7 days / 30 days / 3 months** or a custom date picker.

**Refresh from S3** button triggers an incremental fetch (skips already-imported files).

## AWS credentials

No credentials are stored by this application. Authentication is delegated to the
[AWS SDK default credentials chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html):

1. Environment variables — `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`
2. `~/.aws/credentials` — populated by `aws configure`
3. EC2/ECS instance profile

### Minimum required IAM permissions

```json
{
  "Effect": "Allow",
  "Action": ["s3:ListBucket", "s3:GetObject"],
  "Resource": [
    "arn:aws:s3:::my-cloudfront-logs",
    "arn:aws:s3:::my-cloudfront-logs/*"
  ]
}
```

## Database

Logs are stored in a SQLite file (`logs.db` by default, relative to the working directory).
Query it directly with any SQLite-compatible tool:

```bash
sqlite3 logs.db "SELECT ua_name, COUNT(*) FROM cloudfront_logs GROUP BY ua_name ORDER BY 2 DESC"
```

### Schema

```sql
cloudfront_logs (
    id                        INTEGER PRIMARY KEY,
    timestamp                 TEXT NOT NULL,   -- ISO-8601 UTC
    edge_location             TEXT,
    sc_bytes                  INTEGER,
    client_ip                 TEXT,
    method                    TEXT,
    uri_stem                  TEXT,
    status                    INTEGER,
    referer                   TEXT,
    user_agent                TEXT,
    edge_result_type          TEXT,
    protocol                  TEXT,
    cs_bytes                  INTEGER,
    time_taken                REAL,
    edge_response_result_type TEXT,
    protocol_version          TEXT,
    time_to_first_byte        REAL,
    edge_detailed_result_type TEXT,
    content_type              TEXT,
    content_length            INTEGER,
    country                   TEXT,            -- ISO 3166-1 alpha-2
    ua_name                   TEXT             -- classified user-agent label
)

fetched_files (
    s3_key     TEXT PRIMARY KEY,
    fetched_at TEXT
)
```