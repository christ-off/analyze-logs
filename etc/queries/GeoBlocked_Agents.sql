select cl.ua_name, count(1) nb
from cloudfront_logs cl
where cl.edge_detailed_result_type = 'ClientGeoBlocked'
group by cl.ua_name
order by count(1) desc