import { Charts } from './charts.js';
import { buildBaseParams, renderMinVersionBanner, readMeta } from './utils.js';
import { aggregateByVersion as aggregateByVersionGeneric, sortVersions, createVersionTable } from './version-table.js';

// Extract the Firefox major version from a raw user_agent string, e.g. "...Firefox/151.0" -> 151.
export function firefoxMajorVersion(rawUa) {
    const m = rawUa.match(/Firefox\/(\d+)/);
    return m ? Number(m[1]) : null;
}

export function aggregateByVersion(rawUserAgents, humanStats) {
    return aggregateByVersionGeneric(rawUserAgents, humanStats, firefoxMajorVersion);
}

export { sortVersions };

const versionTable = createVersionTable('Firefox');

async function loadAllCharts() {
    const p = buildBaseParams({});

    Charts.loadChart(`firefox/result-types?${p}`,     d => Charts.pie('chartResultTypes', d, Charts.RESULT_TYPE_COLORS));
    Charts.loadChart(`firefox/countries?${p}`,        d => Charts.pie('chartCountries', d, null));
    Charts.loadChart(`firefox/uri-stems?${p}`,        d => Charts.horizontalStackedBar('chartUriStems', d));
    Charts.loadChart(`firefox/requests-per-day?${p}`, d => Charts.stackedBarByDay('chartRequestsPerDay', d));

    const tbody = document.getElementById('tbodyVersions');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">Loading…</td></tr>';
    document.getElementById('versionBarLegend').style.setProperty('display', 'none', 'important');

    const [rawUserAgents, humanStats] = await Promise.all([
        fetch(`/api/firefox/user-agents?${p}`).then(r => r.json()),
        fetch(`/api/firefox/human-traffic?${p}`).then(r => r.json()),
    ]);
    const esrVersion = Number(readMeta('cf-firefox-esr'));
    renderMinVersionBanner('minFirefoxVersionBanner', 'Firefox', humanStats, undefined, [esrVersion]);

    versionTable.setVersions(aggregateByVersion(rawUserAgents, humanStats));
}

loadAllCharts();
