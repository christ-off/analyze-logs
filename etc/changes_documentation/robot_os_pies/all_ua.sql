select cl.ua_name, count(1)
from cloudfront_logs cl
group by cl.ua_name
order by count(1) desc