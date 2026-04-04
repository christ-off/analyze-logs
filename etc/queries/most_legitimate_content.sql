select cl.uri_stem, count(1)
from cloudfront_logs cl
where cl.status in (200, 304)
group by cl.uri_stem
order by count(1) desc