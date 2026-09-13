import { Charts } from './charts.js';
import { buildBaseParams, detailUrl } from './utils.js';

// The "Top User Agents / Countries / URLs / Referers / Requests per Day" chart row shared by
// fragments/dashboard-rows.html — used by both the main dashboard (dashboard.js, no prefix) and
// the Human page (human.js, 'human-' prefix) against their respective /api endpoints.
export function loadCoreCharts(prefix = '') {
    const p = buildBaseParams({});
    Charts.loadChart(`${prefix}ua-names-split?${p}`,   data => Charts.horizontalStackedBar('chartUaNames', data,
        d => detailUrl('/ua-detail', { ua: d.name })));
    Charts.loadChart(`${prefix}countries?${p}`,        data => Charts.horizontalStackedBar('chartCountries', data,
        item => detailUrl('/country-detail', { country: item.code })));
    Charts.loadChart(`${prefix}top-urls-split?${p}`,   data => Charts.horizontalStackedBar('chartTopUrls',  data,
        d => detailUrl('/url-detail', { url: d.name })));
    Charts.loadChart(`${prefix}referers?${p}`,         data => Charts.horizontalBar('chartReferers', data,
        d => detailUrl('/referer-detail', { referer: d.name })));
    Charts.loadChart(`${prefix}requests-per-day?${p}`, data => Charts.stackedBarByDay('chartRequestsPerDay',   data));
}
