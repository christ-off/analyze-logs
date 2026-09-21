import { Charts } from './charts.js';
import { buildBaseParams, renderMinVersionBanner, readMeta } from './utils.js';
import { createVersionTable } from './version-table.js';
import { BROWSERS, aggregateByVersion } from './browsers.js';
import { initRefresh } from './refresh.js';

const browser = readMeta('cf-browser');
const { label, token } = BROWSERS[browser];
const versionTable = createVersionTable(label);

// Firefox only: the ESR version is exempt from the "min version" banner.
function excludedVersions() {
    const esr = document.querySelector('meta[name="cf-firefox-esr"]');
    return esr ? [Number(esr.content)] : [];
}

export async function loadAllCharts() {
    const p = buildBaseParams({});

    Charts.loadChart(`${browser}/result-types?${p}`,     d => Charts.pie('chartResultTypes', d, Charts.RESULT_TYPE_COLORS));
    Charts.loadChart(`${browser}/countries?${p}`,        d => Charts.pie('chartCountries', d, null));
    Charts.loadChart(`${browser}/uri-stems?${p}`,        d => Charts.horizontalStackedBar('chartUriStems', d));
    Charts.loadChart(`${browser}/requests-per-day?${p}`, d => Charts.stackedBarByDay('chartRequestsPerDay', d));

    const tbody = document.getElementById('tbodyVersions');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">Loading…</td></tr>';
    document.getElementById('versionBarLegend').style.setProperty('display', 'none', 'important');

    const [rawUserAgents, humanStats] = await Promise.all([
        fetch(`/api/${browser}/user-agents?${p}`).then(r => r.json()),
        fetch(`/api/${browser}/human-traffic?${p}`).then(r => r.json()),
    ]);
    renderMinVersionBanner('minVersionBanner', label, humanStats, token, excludedVersions());

    versionTable.setVersions(aggregateByVersion(browser, rawUserAgents, humanStats));
}

initRefresh(loadAllCharts);
await loadAllCharts();
