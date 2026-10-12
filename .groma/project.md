---
type: Groma Project
title: analyze-logs
groma:
  profile: architecture
description: Local dashboard for Amazon CloudFront access logs.
---

Fetches CloudFront standard logs (JSON) from S3 into a local SQLite database and shows charts and drill-down pages about traffic, bots, countries, referers and security probing. Intended for a single local user; there is no authentication.
