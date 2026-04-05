select cl.user_agent, cl.status, count(1) nb
from cloudfront_logs cl
where cl.status in (200, 304, 302)
group by cl.user_agent, cl.status
order by count(1) desc