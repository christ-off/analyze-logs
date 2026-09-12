import { Charts } from './charts.js';
import { buildBaseParams, renderMinVersionBanner } from './utils.js';
import { aggregateByVersion as aggregateByVersionGeneric, sortVersions, createVersionTable } from './version-table.js';

// Extract the Edge major version from a raw user_agent string, e.g.
// "...Chrome/144.0.0.0 Safari/537.36 Edg/144.0.0.0" -> 144. Reads the "Edg/" token, not the
// "Chrome/" one the same UA also carries — Edge can lag behind its underlying Chromium version.
export function edgeMajorVersion(rawUa) {
    const m = rawUa.match(/Edg\/(\d+)/);
    return m ? Number(m[1]) : null;
}

export function aggregateByVersion(rawUserAgents, humanStats) {
    return aggregateByVersionGeneric(rawUserAgents, humanStats, edgeMajorVersion);
}

export { sortVersions };

const versionTable = createVersionTable('Edge');

async function loadAllCharts() {
    const p = buildBaseParams({});

    Charts.loadChart(`edge/result-types?${p}`,     d => Charts.pie('chartResultTypes', d, Charts.RESULT_TYPE_COLORS));
    Charts.loadChart(`edge/countries?${p}`,        d => Charts.pie('chartCountries', d, null));
    Charts.loadChart(`edge/uri-stems?${p}`,        d => Charts.horizontalStackedBar('chartUriStems', d));
    Charts.loadChart(`edge/requests-per-day?${p}`, d => Charts.stackedBarByDay('chartRequestsPerDay', d));

    const tbody = document.getElementById('tbodyVersions');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">Loading…</td></tr>';
    document.getElementById('versionBarLegend').style.setProperty('display', 'none', 'important');

    const [rawUserAgents, humanStats] = await Promise.all([
        fetch(`/api/edge/user-agents?${p}`).then(r => r.json()),
        fetch(`/api/edge/human-traffic?${p}`).then(r => r.json()),
    ]);
    renderMinVersionBanner('minEdgeVersionBanner', 'Edge', humanStats, 'Edg');

    versionTable.setVersions(aggregateByVersion(rawUserAgents, humanStats));
}

loadAllCharts();
