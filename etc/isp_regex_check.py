# Lists the blockedBotRegex alternatives that still match traffic from ASNs categorised 'isp'.
# Others are already caught by the "not an ISP" ASN rule. Run from the repo root: python3 -I etc/isp_regex_check.py
# ASN is only recorded recently; older rows use the ASN seen later for the same IP.
import sqlite3, re, collections
R=r"linkupbot\/|sleepbot|got \(https:\/\/github\.com\/sindresorhus\/got|palo alto networks|trident|amzn-searchbot\/|ccbot\/|aiohttp\/|emacs\/|meta-webindexer\/|twitterbot\/1\.0|presto|analyseseonet\/|scrapy|headlesschrome|aranea web-crawled corpora project|pimeyes-downloader-api|bytespider|python-httpx\/|mach-o|intelx\.io_bot|welley\/|searchenginebot|python-requests\/|databankmetasearch|shapbot|cms-detector\/|navcrawl\/|wellknownbot|siteauditbot\/|ev-crawler|builtwith|timpibot|xai-searchbot\/|semrushbot|livelapbot\/|engagemiibot\/|sitescan\/|stackyenrich\/|testsearchspider|atlas-enrich\/|fyndbot|wpbot\/|googlebot|dataforseobot|imagebot\/|loadedbot\/|google-cloudvertexbot|googleother|koofie\.net\/|domain-intel\/|screaming frog seo spider|openclaw|discordbot\/|sharkey \(like|reflectionbot\/|lightpanda\/|forestengine\/|seojuice-searchbot\/|coccocbot|hubspot crawler|domain-harvester\/|mapthenetbot\/|expansel-monitor\/|fogbot\/|newsletterformresearchbot\/|srchs-research-bot\/|aionbot\/|tiktokspider|opentheboxbot\/|veryhip\/|cms-security-auditor\/|censysinspect\/|publicwwwbot\/|wp2shell|webatlabot|ssi-nutch\/|variableratio-publicassetresearch\/|halobot\/|flowb0t-contentengine\/|claritybot\/|undici|serpex-index\/|compatible; crawler\)|webapp-mapper\/|ironfountain-leads\/|what10bot\/|konqueror\/|brightbot|rust-scrape\/|crawl-engine\/|keenablebot\/|wordpress\/|curl\/|tphotobot\/|websiteresearch\/|surdotlybot\/|aonsightmetabot\/|giantcrawler\/|baiduspider|sqlspider\/|goscan-mini\/|storebot-google\/|feedfetcher-google|googleimageproxy|app::jupiter\/|httpx - open-source project"
alts = R.split('|')
c = sqlite3.connect('file:logs.db?mode=ro', uri=True)
cat = dict(c.execute("select asn,category from asn_info"))
ipasn = dict(c.execute("select client_ip,asn from cloudfront_logs where asn is not null group by client_ip"))
keep = collections.Counter(); uas = collections.defaultdict(set)
for ua, asn, ip in c.execute("select user_agent,asn,client_ip from cloudfront_logs"):
    a = asn or ipasn.get(ip)
    if cat.get(a) != 'isp': continue
    for x in alts:
        if re.search(x, ua or '', re.I): keep[x] += 1; uas[x].add((ua or '')[:70])
for x, n in keep.most_common(): print(n, x, list(uas[x])[:2])
print('|'.join(x for x, _ in keep.most_common()))
