# analyze-logs

CLI tool to fetch [Amazon S3 Server Access Logs](https://docs.aws.amazon.com/AmazonS3/latest/userguide/ServerLogs.html) and store them in a local SQLite database for later analysis.

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
  -b, --bucket       S3 bucket containing the access logs        (required)
  -p, --prefix       S3 key prefix for log files                 (default: root)
  -r, --region       AWS region                                  (default: us-east-1)
      --profile      AWS credentials profile name
  -d, --db           SQLite database file path                   (default: logs.db)
      --since        Only fetch files modified on or after date  (yyyy-MM-dd)
      --skip-existing  Skip files already imported               (default: true)
```

**Example — first run:**
```bash
./analyze-logs fetch \
  --bucket my-website-logs \
  --prefix access-logs/ \
  --region eu-west-3
```

**Example — incremental update (only new files since Jan 1):**
```bash
./analyze-logs fetch \
  --bucket my-website-logs \
  --prefix access-logs/ \
  --since 2025-01-01
```

**Example — use a named AWS profile:**
```bash
./analyze-logs fetch \
  --bucket my-website-logs \
  --profile my-aws-profile
```

### `status` — show database statistics

```
./analyze-logs status [-d <db>]
```

```
Database : logs.db
Entries  : 42381
Earliest : 2024-06-01T00:03:12Z[UTC]
Latest   : 2025-03-28T23:58:01Z[UTC]
Buckets  : 1
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

This writes credentials to `~/.aws/credentials`:

```ini
[default]
aws_access_key_id     = AKIAIOSFODNN7EXAMPLE
aws_secret_access_key = wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
```

**This file is stored on your local machine only** and is never read or copied by this tool — the AWS SDK reads it directly.

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
        "arn:aws:s3:::my-website-logs",
        "arn:aws:s3:::my-website-logs/*"
      ]
    }
  ]
}
```

## Database

Logs are stored in a SQLite file (`logs.db` by default). You can query it directly with any SQLite-compatible tool (DBeaver, DB Browser for SQLite, `sqlite3` CLI, datasette, etc.):

```bash
sqlite3 logs.db "SELECT operation, COUNT(*), AVG(total_time_ms) FROM access_logs GROUP BY operation"
```

### Schema

```sql
access_logs (
    id              INTEGER PRIMARY KEY,
    bucket_owner    TEXT,
    bucket          TEXT,
    time            TEXT,       -- ISO-8601 with timezone
    remote_ip       TEXT,
    requester       TEXT,       -- NULL if anonymous
    request_id      TEXT,
    operation       TEXT,       -- e.g. REST.GET.OBJECT
    key             TEXT,       -- S3 object key requested
    request_uri     TEXT,       -- full HTTP request line
    http_status     INTEGER,
    error_code      TEXT,       -- NULL on success
    bytes_sent      INTEGER,    -- NULL if unknown
    object_size     INTEGER,    -- NULL if unknown
    total_time_ms   INTEGER,
    turnaround_ms   INTEGER,
    referrer        TEXT,
    user_agent      TEXT,
    version_id      TEXT
)

fetched_files (
    s3_key          TEXT PRIMARY KEY,   -- tracks which log files have been imported
    fetched_at      TEXT
)
```

## Enabling S3 access logging

If not already enabled on your bucket, turn it on in the AWS Console under **S3 → your bucket → Properties → Server access logging**, or via the CLI:

```bash
aws s3api put-bucket-logging \
  --bucket my-website \
  --bucket-logging-status '{
    "LoggingEnabled": {
      "TargetBucket": "my-website-logs",
      "TargetPrefix": "access-logs/"
    }
  }'
```
