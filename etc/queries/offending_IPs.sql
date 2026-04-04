select cl.client_ip,
       count(1) nb_err
from cloudfront_logs cl
where cl.status not in (200, 304)
group by cl.client_ip
order by count(1) desc