# Deploy AnalyzeLogs to AWS — Research

## Application Profile

| Attribute | Value |
|-----------|-------|
| Type | Spring Boot 4 web application (Java 25) |
| Packaging | Executable JAR |
| Database | SQLite (file-based, `logs.db`) |
| External dependency | AWS S3 (read CloudFront logs) |
| Users | Single-user, personal tool |
| Authentication | None (by design) |
| Memory footprint | Low (Spring Boot + SQLite, no connection pool pressure) |
| CPU profile | Bursty — idle between refreshes, query spikes on dashboard load |
| State | Stateful (SQLite file must persist across restarts) |
| Docker | Not yet containerized |

**Key constraint**: SQLite is a local file. Any deployment option must either persist that file (EFS, EBS, instance storage) or require a database migration to a managed service (RDS/Aurora).

---

## Option 1 — EC2 (simplest path)

Run the JAR directly on a Linux EC2 instance, exactly as it runs locally.

**Architecture**
```
Internet → EC2 instance (t4g.small)
             ├── Java 25 JVM running analyze-logs.jar
             ├── /data/logs.db  (EBS volume, gp3)
             └── IAM instance profile → S3 bucket access
```

**Setup steps**
1. Launch EC2 Amazon Linux 2023 instance
2. Install Temurin 25 JDK
3. Copy JAR + `application-local.yml`
4. Attach `application-local.yml` via SSM Parameter Store or Secrets Manager
5. Create systemd unit to run and auto-restart the JAR
6. Attach IAM role with `s3:ListBucket` + `s3:GetObject` on the log bucket
7. Security group: inbound 8080 from your IP only

**AWS services used**
- EC2 (t4g.small or t3.micro)
- EBS gp3 (20 GB)
- IAM instance profile
- (Optional) Systems Manager Session Manager — no SSH key needed

**Cost estimate (eu-west-1, on-demand)**

| Resource | Monthly |
|----------|---------|
| EC2 t4g.small | ~$12 |
| EBS gp3 20 GB | ~$1.60 |
| Data transfer out (minimal) | ~$0.50 |
| **Total** | **~$14/month** |

Reserved 1yr no-upfront: ~$8/month total.

**Pros**: Zero changes to application code. Direct Java 25 support. Full control.  
**Cons**: You manage patching, JDK upgrades, instance lifecycle. No auto-scaling (not needed here).

---

## Option 2 — Elastic Beanstalk (managed EC2)

AWS-managed PaaS layer on top of EC2. Upload the JAR, Beanstalk handles deployment, health checks, and OS patching.

**Architecture**
```
Internet → Elastic Beanstalk env (Java 21 platform*)
             └── EC2 instance → JAR → /var/app/current/logs.db (EBS)
```

*Beanstalk Java platform is currently pinned to Corretto 21. Java 25 would require a custom platform or Docker-based platform.

**Cost estimate**

| Resource | Monthly |
|----------|---------|
| EC2 t4g.small (same as above) | ~$12 |
| EBS gp3 20 GB | ~$1.60 |
| Beanstalk management | Free |
| **Total** | **~$14/month** |

**Pros**: Managed health checks, rolling deploys, CloudWatch integration out of the box.  
**Cons**: Java platform constraint (Corretto 21, not 25). Adds Beanstalk complexity with no real benefit over raw EC2 for a single-user tool.

**Verdict**: Not recommended. EC2 gives same cost with more control and Java 25 freedom.

---

## Option 3 — ECS Fargate + EFS (containerized, serverless compute)

Containerize the app, run it on Fargate with an EFS mount for the SQLite file.

**Architecture**
```
Internet → ALB (optional) → ECS Fargate task
                              ├── analyze-logs container (0.5 vCPU, 1 GB)
                              └── EFS mount → /data/logs.db
                            IAM task role → S3 bucket
```

**Prerequisites**
- Write a `Dockerfile` (multi-stage: Maven build + JRE 25 runtime)
- Push image to ECR
- Create EFS file system + access point
- ECS task definition with EFS volume mount

**Cost estimate**

| Resource | Monthly |
|----------|---------|
| Fargate (0.5 vCPU × 730h) | ~$22 |
| Fargate (1 GB memory × 730h) | ~$5 |
| EFS storage (5 GB) | ~$1.50 |
| ECR image storage | ~$0.10 |
| ALB (optional, skip for personal use) | ~$18 |
| **Total (no ALB)** | **~$29/month** |
| **Total (with ALB)** | **~$47/month** |

Fargate with scheduled stop (run only during working hours ~10h/day):
~$10/month compute + EFS.

**Pros**: No instance management. Easy rolling deploys via new task revision. Scales to zero with stop/start.  
**Cons**: More expensive than EC2 for always-on. EFS adds latency to SQLite I/O (SQLite on NFS is not ideal — WAL mode helps but concurrent writes still problematic if ever needed). Complex initial setup.

**Verdict**: Viable but over-engineered for a single-user personal tool. Only justified if you want full managed infrastructure with zero EC2 management.

---

## Option 4 — App Runner

Fully managed container service. Provide the image, AWS handles deployment, scaling, TLS, health checks.

**Architecture**
```
Internet (HTTPS) → App Runner service
                    └── analyze-logs container (0.25 vCPU, 0.5 GB)
                    (No persistent volume — SQLite data lost on restart)
```

**Critical limitation**: App Runner does not support persistent volumes. SQLite data is ephemeral. Every deployment or restart wipes `logs.db`.

This means App Runner is only viable if the database is migrated to an external service (RDS/Aurora Serverless) or if the app is modified to re-fetch all S3 logs on every startup (acceptable if the bucket has all historical data and startup time is tolerable).

**Cost estimate**

| Resource | Monthly |
|----------|---------|
| App Runner (0.25 vCPU active compute) | ~$14 |
| App Runner (0.5 GB memory) | ~$7 |
| RDS t4g.micro PostgreSQL (if migrating) | ~$12 |
| **Total (with RDS)** | **~$33/month** |

**Pros**: Zero ops. Auto TLS. Pause when no traffic (reduces cost).  
**Cons**: Requires database migration or full re-fetch on every restart. More expensive than EC2.

**Verdict**: Not recommended as-is. Requires significant code changes (database driver swap + Liquibase dialect).

---

## Option 5 — AWS Lightsail (budget VPS)

AWS's simplified VPS offering. Predictable flat-rate pricing, simpler console than EC2.

**Architecture**: Identical to EC2 Option 1.

**Cost estimate**

| Plan | vCPU | RAM | Storage | Price |
|------|------|-----|---------|-------|
| $5/mo | 1 | 1 GB | 40 GB SSD | **$5/month** |
| $10/mo | 1 | 2 GB | 60 GB SSD | $10/month |

Java 25 + Spring Boot 4 runs fine on 1 GB RAM (Spring Boot 4 is leaner than 3.x on virtual threads).

**Pros**: Cheapest option (~$5/month). Includes static IP, DNS, and 1 TB data transfer.  
**Cons**: Lightsail API/SDK is separate from main AWS SDK. Less IAM granularity. For S3 access, must use IAM user keys (not instance profile), or use Lightsail's VPC peering to reach main account S3.

**Verdict**: Best price-to-value for a personal tool. Main friction is IAM credential management (no instance profile natively).

---

## Option 6 — Lambda + API Gateway (serverless)

Convert the Spring Boot app to a Lambda function using AWS Lambda Web Adapter or Spring Cloud Function.

**Critical limitations**:
- SQLite cannot be used with Lambda (ephemeral `/tmp`, 512 MB–10 GB, not persistent). Would require S3-backed SQLite (Litestream) or migration to DynamoDB/Aurora Serverless.
- Spring Boot cold start on Lambda is 3–8 seconds without SnapStart. With SnapStart (Corretto 21 only today, Java 25 not yet supported): ~1 second.
- Java 25 is not yet a supported Lambda runtime (as of April 2026). Would require custom runtime layer.

**Cost estimate (if feasible)**

| Resource | Monthly (100 req/day) |
|----------|----------------------|
| Lambda (128 MB, 3s) | ~$0.10 |
| API Gateway (HTTP) | ~$0.30 |
| S3 (Litestream sync) | ~$1.00 |
| **Total** | **~$1.50/month** |

**Pros**: Near-zero cost for low traffic.  
**Cons**: Major refactoring required. Java 25 not supported. SQLite not viable. Cold starts. Chart.js sessions become stateless API calls only (no issue — app already does this via `/api/*` endpoints). Not worth the effort for a personal tool.

**Verdict**: Not recommended. Cost savings don't justify the refactoring complexity and runtime constraints.

---

## Option 7 — EC2 + RDS Migration (production-grade)

Replace SQLite with RDS PostgreSQL and deploy on EC2 or ECS. Enables true stateless deployment.

**Required changes**:
- Add `postgresql` JDBC driver, remove `sqlite-jdbc`
- Update `application.yml` datasource URL + dialect
- Review Liquibase changesets (SQLite-specific pragmas: `PRAGMA journal_mode=WAL` must be removed)
- Review SQL in `DashboardService` for SQLite-specific syntax (e.g., `strftime()` → `date_trunc()`)
- Test all repository queries

**Cost estimate (EC2 t4g.small + RDS t4g.micro PostgreSQL)**

| Resource | Monthly |
|----------|---------|
| EC2 t4g.small | ~$12 |
| RDS t4g.micro PostgreSQL (single-AZ) | ~$12 |
| EBS 20 GB gp3 (app server) | ~$1.60 |
| RDS storage 20 GB gp2 | ~$2.30 |
| **Total** | **~$28/month** |

**Pros**: Production-ready. Enables horizontal scaling (multiple app instances). Standard PostgreSQL tooling.  
**Cons**: Significant code changes. Overkill for a single-user personal tool.

**Verdict**: Only justified if the app evolves into a multi-user or production service.

---

## Comparison Matrix

| Option | Monthly Cost | Code Changes | Ops Complexity | Java 25 | SQLite |
|--------|-------------|--------------|----------------|---------|--------|
| EC2 (direct) | ~$8–14 | None | Low | Yes | Yes (EBS) |
| Elastic Beanstalk | ~$14 | None | Low-Medium | No* | Yes (EBS) |
| ECS Fargate + EFS | ~$29 | Dockerfile | Medium | Yes | Yes (EFS) |
| App Runner | ~$33 | Dockerfile + DB migration | Low | Yes | No |
| Lightsail | ~$5 | None | Low | Yes | Yes |
| Lambda | ~$1.50 | Major refactor | High | No | No |
| EC2 + RDS | ~$28 | DB migration | Medium | Yes | No |

\* Beanstalk Java platform uses Corretto 21; Java 25 requires Docker platform.

---

## Recommendation

### Short-term: AWS Lightsail ($5/month)

For a personal single-user tool, Lightsail offers the best balance of cost and simplicity:

1. Create a Lightsail instance (Debian/Amazon Linux, 1 GB RAM plan)
2. Install Eclipse Temurin 25
3. Copy JAR + config
4. Create systemd service
5. Create an IAM user with scoped S3 read-only policy, store keys in `application-local.yml`
6. Configure Lightsail static IP
7. Access via `http://static-ip:8080` (or add Lightsail CDN/load balancer for HTTPS)

Total effort: ~2 hours. Zero code changes required.

### If containerization is desired: EC2 + Docker

Add a `Dockerfile`, deploy on a `t4g.small` EC2 instance with Docker, mount an EBS volume at `/data`. This gives a repeatable deployment without Fargate costs.

```dockerfile
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY target/analyze-logs-*.jar app.jar
VOLUME /data
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", \
            "-jar", "app.jar", \
            "--app.db-path=/data/logs.db"]
```

Run with: `docker run -v /data:/data -p 8080:8080 --env-file .env analyze-logs`

### If multi-user or CI/CD is needed: ECS Fargate + RDS PostgreSQL

Migrate database to RDS PostgreSQL, containerize, deploy to Fargate. Estimated cost: ~$40/month. Only justified if the tool becomes a shared service.

---

## IAM Permissions (all options)

Minimum policy for S3 read access (attach to instance profile or IAM user):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:ListBucket"],
      "Resource": "arn:aws:s3:::YOUR-LOG-BUCKET"
    },
    {
      "Effect": "Allow",
      "Action": ["s3:GetObject"],
      "Resource": "arn:aws:s3:::YOUR-LOG-BUCKET/YOUR-PREFIX/*"
    }
  ]
}
```

EC2/ECS deployments should use IAM instance profiles / task roles — never embed AWS keys in config files.
