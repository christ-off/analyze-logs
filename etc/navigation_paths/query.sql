-- Top 10 cross-page navigation paths from CloudFront logs
-- A navigation = sequence of 2+ requests by the same IP + user agent to different content pages
-- Filters: human browsers only, excludes static assets, WP scanners, bots

-- 2-hop paths (top 10)
WITH ordered AS (
  SELECT
    client_ip,
    user_agent,
    timestamp,
    CASE
      WHEN uri_stem LIKE '%-%' AND uri_stem NOT LIKE '/wp-%' AND uri_stem NOT LIKE '/Admin%'
        THEN TRIM(REPLACE(REPLACE(LOWER(TRIM(uri_stem, '/')), '/', '-'), '-/', '/'), '/')
      WHEN uri_stem IN ('/coups_de_coeur/', '/meilleurs_ebooks_sans_drm/')
        THEN TRIM(uri_stem, '/')
      ELSE uri_stem
    END AS bare_uri,
    ROW_NUMBER() OVER (PARTITION BY client_ip, user_agent ORDER BY timestamp) AS rn
  FROM cloudfront_logs
  WHERE uri_stem NOT LIKE '%.%'
    AND uri_stem != '/robots.txt'
    AND uri_stem NOT LIKE '/wp-%'
    AND uri_stem NOT LIKE '/Admin%'
    AND uri_stem NOT LIKE '//%'
    AND (
      uri_stem LIKE '%-%'
      OR uri_stem IN ('/coups_de_coeur/', '/meilleurs_ebooks_sans_drm/')
    )
    AND ua_name IN ('Chrome / Windows', 'Chrome / macOS', 'Chrome / Linux', 'Chrome / Android',
                     'Firefox / Windows', 'Firefox / Linux', 'Safari / macOS', 'Safari / iPhone')
),
pairs AS (
  SELECT
    prev.bare_uri || ' → ' || next.bare_uri AS path,
    COUNT(*) AS cnt
  FROM ordered prev
  JOIN ordered next
    ON prev.client_ip = next.client_ip
   AND prev.user_agent = next.user_agent
   AND next.rn = prev.rn + 1
  WHERE next.bare_uri IS NOT NULL
    AND prev.bare_uri != next.bare_uri
  GROUP BY path
)
SELECT path, cnt
FROM pairs
WHERE path NOT LIKE '%wp-%'
ORDER BY cnt DESC
LIMIT 10;

-- 3-hop paths (uncomment to run)
-- WITH ordered AS ( ... same as above ... ),
-- triples AS (
--   SELECT t1.bare_uri || ' → ' || t2.bare_uri || ' → ' || t3.bare_uri AS path
--   FROM ordered t1
--   JOIN ordered t2 ON t1.client_ip = t2.client_ip AND t1.user_agent = t2.user_agent AND t2.rn = t1.rn + 1
--   JOIN ordered t3 ON t1.client_ip = t3.client_ip AND t1.user_agent = t3.user_agent AND t3.rn = t2.rn + 1
--   WHERE t2.bare_uri IS NOT NULL AND t3.bare_uri IS NOT NULL
--     AND t1.bare_uri != t2.bare_uri AND t2.bare_uri != t3.bare_uri
-- )
-- SELECT path, COUNT(*) AS cnt
-- FROM triples
-- GROUP BY path
-- HAVING cnt >= 2
-- ORDER BY cnt DESC
-- LIMIT 10;
