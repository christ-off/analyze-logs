-- Suspicious user agents: hitting PHP/Wordpress URLs 10+ times per day
-- and NOT in the list of agents making legitimate requests (10+ legit requests total)
--
-- Legitimate = Hit or Miss, no errors (4xx/5xx), not on PHP/Wordpress URLs, non-empty UA
-- Empty user_agent is never legitimate — excluded upfront from both CTEs

WITH non_legit_agents AS (
  -- Agents hitting PHP/WP URLs 10+ times per day (empty UA excluded: always suspicious)
  SELECT user_agent,
         date(timestamp) AS day,
         count(1)        AS nb_requests
  FROM cloudfront_logs
  WHERE user_agent != ''
    AND (   uri_stem LIKE '%.php%'
         OR uri_stem LIKE '/wp%')
  GROUP BY user_agent, date(timestamp)
  HAVING count(1) >= 2
),
legit_agents AS (
  -- Agents with at least 10 legitimate requests (empty UA excluded: never legitimate)
  SELECT user_agent
  FROM cloudfront_logs
  WHERE user_agent != ''
    AND edge_result_type IN ('Hit', 'Miss')
    AND status NOT IN (400, 403, 404, 503)
    AND uri_stem NOT LIKE '%.php%'
    AND uri_stem NOT LIKE '/wp%'
  GROUP BY user_agent
  HAVING count(1) >= 2
)
SELECT nla.user_agent,
       nla.day,
       nla.nb_requests
FROM non_legit_agents nla
WHERE nla.user_agent NOT IN (SELECT user_agent FROM legit_agents)
ORDER BY nla.day, nla.nb_requests DESC;
