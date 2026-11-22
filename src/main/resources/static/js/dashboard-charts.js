import { Charts } from './charts.js';
import { buildBaseParams, detailUrl } from './utils.js';

// The "user agents" bar chart shared by every page showing classified ua_names (main dashboard,
// Human page, country-detail, asn-detail, url-detail, feed-readers, covers, and bot-analysis'
// "Most Active Bots"): a horizontal stacked bar over the page's own endpoint, rendered into
// `canvasId`, with every row linking to /ua-detail. (Tables of raw user_agent strings instead
// link to /ua-requests — see uaRequestsUrl.)
export function loadUaChart(canvasId, endpoint) {
    Charts.loadChart(endpoint, data => Charts.horizontalStackedBar(canvasId, data,
        d => detailUrl('/ua-detail', { ua: d.name })));
}

// The "Top User Agents / Countries / URLs / Referers / Requests per Day" chart row shared by
// fragments/dashboard-rows.html — used by both the main dashboard (dashboard.js, no prefix) and
// the Human page (human.js, 'human-' prefix) against their respective /api endpoints.
export function loadCoreCharts(prefix = '') {
    const p = buildBaseParams({});
    loadUaChart('chartUaNames', `${prefix}ua-names-split?${p}`);
    Charts.loadChart(`${prefix}countries?${p}`,        data => Charts.horizontalStackedBar('chartCountries', data,
        item => detailUrl('/country-detail', { country: item.code })));
    Charts.loadChart(`${prefix}top-urls-split?${p}`,   data => Charts.horizontalStackedBar('chartTopUrls',  data,
        d => detailUrl('/url-detail', { url: d.name })));
    Charts.loadChart(`${prefix}referers?${p}`,         data => Charts.horizontalBar('chartReferers', data,
        d => detailUrl('/referer-detail', { referer: d.name })));
    Charts.loadChart(`${prefix}requests-per-day?${p}`, data => Charts.stackedBarByDay('chartRequestsPerDay',   data));
}
