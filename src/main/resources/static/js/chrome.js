import { Charts } from './charts.js';
import { buildBaseParams, renderMinVersionBanner } from './utils.js';
import { aggregateByVersion as aggregateByVersionGeneric, sortVersions, createVersionTable } from './version-table.js';

// Extract the Chrome major version from a raw user_agent string, e.g. "...Chrome/120.0.0.0..." -> 120.
export function chromeMajorVersion(rawUa) {
    const m = rawUa.match(/Chrome\/(\d+)/);
    return m ? Number(m[1]) : null;
}

export function aggregateByVersion(rawUserAgents, humanStats) {
    return aggregateByVersionGeneric(rawUserAgents, humanStats, chromeMajorVersion);
}

export { sortVersions };

const versionTable = createVersionTable('Chrome');

async function loadAllCharts() {
    const p = buildBaseParams({});

    Charts.loadChart(`chrome/result-types?${p}`,     d => Charts.pie('chartResultTypes', d, Charts.RESULT_TYPE_COLORS));
    Charts.loadChart(`chrome/countries?${p}`,        d => Charts.pie('chartCountries', d, null));
    Charts.loadChart(`chrome/uri-stems?${p}`,        d => Charts.horizontalStackedBar('chartUriStems', d));
    Charts.loadChart(`chrome/requests-per-day?${p}`, d => Charts.stackedBarByDay('chartRequestsPerDay', d));

    const tbody = document.getElementById('tbodyVersions');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">Loading…</td></tr>';
    document.getElementById('versionBarLegend').style.setProperty('display', 'none', 'important');

    const [rawUserAgents, humanStats] = await Promise.all([
        fetch(`/api/chrome/user-agents?${p}`).then(r => r.json()),
        fetch(`/api/chrome/human-traffic?${p}`).then(r => r.json()),
    ]);
    renderMinVersionBanner('minChromeVersionBanner', 'Chrome', humanStats);

    versionTable.setVersions(aggregateByVersion(rawUserAgents, humanStats));
}

loadAllCharts();
