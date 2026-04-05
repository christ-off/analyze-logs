select
    cl.ua_name, cl.user_agent
from cloudfront_logs cl
group by cl.ua_name
having count(2) >= 2