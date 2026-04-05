select cl.user_agent,
       count(1) nb
from cloudfront_logs cl
where cl.status not in (200, 304)
group by cl.user_agent
order by count(1) desc