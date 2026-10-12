---
type: C4 Actor
title: Site owner
status: stable
groma:
  id: site-owner
---

Runs the app locally to understand who visits their website: humans versus bots, countries, referers, scanners. Triggers log refreshes from S3 and maintains the user-agent and referer rules.

## Relationships

| Source | Target | Description | Technology |
| --- | --- | --- | --- |
| [site-owner](site-owner.md) | [dashboard-frontend](../systems/analyze-logs/containers/dashboard-frontend/container.md) | Browses dashboards | HTTP |
