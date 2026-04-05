select cl.ua_name, cl.status, count(1) nb
from cloudfront_logs cl
where cl.status in (200, 304, 302)
group by cl.ua_name, cl.status
order by count(1) desc