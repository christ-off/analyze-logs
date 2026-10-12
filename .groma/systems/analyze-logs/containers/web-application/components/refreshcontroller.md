---
type: C4 Component
title: Log ingestion
status: stable
groma:
  id: refreshcontroller
  parent: web-application
  code:
    - scanner: java
      file: src/main/java/com/example/analyzelog/web/RefreshController.java
      symbol: RefreshController
    - scanner: java
      file: src/main/java/com/example/analyzelog/service/FetchService.java
    - scanner: java
      file: src/main/java/com/example/analyzelog/fetcher/S3LogFetcher.java
      symbol: S3LogFetcher
    - scanner: java
      file: src/main/java/com/example/analyzelog/parser/CloudFrontLogParser.java
      symbol: CloudFrontLogParser
    - scanner: java
      file: src/main/java/com/example/analyzelog/model/CloudFrontLogEntry.java
      symbol: CloudFrontLogEntry
    - scanner: java
      file: src/main/java/com/example/analyzelog/service/EdgeLocationResolver.java
description: Fetches and imports CloudFront logs from S3.
---

The Refresh action starts an incremental fetch that lists S3 objects, parses CloudFront JSON log lines, resolves edge locations, and stores new rows; progress is polled by the frontend.

## Relationships

| Source | Target | Description | Technology |
| --- | --- | --- | --- |
| [refreshcontroller](refreshcontroller.md) | [amazon-s3](../../../../../externals/amazon-s3.md) | Downloads log files | AWS SDK |
| [src/main/java/com/example/analyzelog/web/RefreshController.java](../../../../../../src/main/java/com/example/analyzelog/web/RefreshController.java) | [src/main/java/com/example/analyzelog/repository/LogRepository.java](../../../../../../src/main/java/com/example/analyzelog/repository/LogRepository.java) | Stores imported rows | Java call |
