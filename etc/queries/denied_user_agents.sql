select cl.ua_name,
       count(1) nb
from cloudfront_logs cl
where cl.status not in (200, 304)
group by cl.ua_name
order by count(1) desc