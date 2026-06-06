with agent_no_slash as (
    select user_agent, client_ip, uri_stem, country, timestamp
    from cloudfront_logs
    where
        uri_stem not like '%.%'
      and uri_stem not like '%/'
      and edge_response_result_type in ('Hit', 'Miss')
    group by user_agent
)
select ans.timestamp, ans.client_ip, ans.user_agent, ans.uri_stem, ans.country, cl.uri_stem, cl.timestamp, cl.edge_response_result_type
from agent_no_slash ans
         inner join cloudfront_logs cl on
    cl.user_agent = ans.user_agent
        and cl.uri_stem like '%.%'
        and cl.uri_stem not in ('/robots.txt', '/feed.xml','/ads.txt', '/rss.xml', '/sitemap.xml')
        and cl.edge_response_result_type in ('Hit', 'Miss')
order by ans.timestamp desc