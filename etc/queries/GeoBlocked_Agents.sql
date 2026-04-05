select cl.user_agent, count(1) nb
from cloudfront_logs cl
where cl.edge_detailed_result_type = 'ClientGeoBlocked'
group by cl.user_agent
order by count(1) desc