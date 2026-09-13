import { Charts } from './charts.js';
import { buildBaseParams, renderMinVersionBanner } from './utils.js';
import { aggregateByVersion as aggregateByVersionGeneric, sortVersions, createVersionTable } from './version-table.js';
import { initRefresh } from './refresh.js';

// Extract the Safari major version from a raw user_agent string, e.g.
// "...Version/26.4 Safari/605.1.15" -> 26. Reads the "Version/" token, not the trailing
// "Safari/" one — that carries the WebKit build number, not the Safari version.
export function safariMajorVersion(rawUa) {
    const m = rawUa.match(/Version\/(\d+)/);
    return m ? Number(m[1]) : null;
}

export function aggregateByVersion(rawUserAgents, humanStats) {
    return aggregateByVersionGeneric(rawUserAgents, humanStats, safariMajorVersion);
}

export { sortVersions };

const versionTable = createVersionTable('Safari');

export async function loadAllCharts() {
    const p = buildBaseParams({});

    Charts.loadChart(`safari/result-types?${p}`,     d => Charts.pie('chartResultTypes', d, Charts.RESULT_TYPE_COLORS));
    Charts.loadChart(`safari/countries?${p}`,        d => Charts.pie('chartCountries', d, null));
    Charts.loadChart(`safari/uri-stems?${p}`,        d => Charts.horizontalStackedBar('chartUriStems', d));
    Charts.loadChart(`safari/requests-per-day?${p}`, d => Charts.stackedBarByDay('chartRequestsPerDay', d));

    const tbody = document.getElementById('tbodyVersions');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">Loading…</td></tr>';
    document.getElementById('versionBarLegend').style.setProperty('display', 'none', 'important');

    const [rawUserAgents, humanStats] = await Promise.all([
        fetch(`/api/safari/user-agents?${p}`).then(r => r.json()),
        fetch(`/api/safari/human-traffic?${p}`).then(r => r.json()),
    ]);
    renderMinVersionBanner('minSafariVersionBanner', 'Safari', humanStats, 'Version');

    versionTable.setVersions(aggregateByVersion(rawUserAgents, humanStats));
}

loadAllCharts();
initRefresh(loadAllCharts);
