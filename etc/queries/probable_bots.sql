-- Probable bots: user agents that visited /robots.txt then other pages within 1 hour
-- from the same client_ip with the same user_agent
-- Results sorted by total request count (descending)

SELECT user_agent,
       count(1) AS nb_requests
FROM cloudfront_logs
WHERE user_agent != ''
  AND client_ip IN (
    -- Find IPs that have both robots.txt and other visits within 1 hour
    SELECT DISTINCT c1.client_ip
    FROM cloudfront_logs c1
    JOIN cloudfront_logs c2
      ON c1.client_ip = c2.client_ip
      AND c1.user_agent = c2.user_agent
      AND c1.uri_stem = '/robots.txt'
      AND c2.uri_stem != '/robots.txt'
      AND datetime(c2.timestamp) > datetime(c1.timestamp)
      AND datetime(c2.timestamp) <= datetime(c1.timestamp, '+1 hour')
    WHERE c1.user_agent != ''
  )
GROUP BY user_agent
ORDER BY nb_requests DESC;