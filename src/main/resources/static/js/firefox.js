import { Charts } from './charts.js';
import { buildBaseParams, resultTotal, stackedBar, renderMinVersionBanner, readMeta } from './utils.js';

// Extract the Firefox major version from a raw user_agent string, e.g. "...Firefox/151.0" -> 151.
export function firefoxMajorVersion(rawUa) {
    const m = rawUa.match(/Firefox\/(\d+)/);
    return m ? Number(m[1]) : null;
}

// Fold per-raw-UA result-type sums and human-traffic stats (one row per raw user_agent string,
// across every OS) down into one row per Firefox major version. Unordered — the table sorts.
export function aggregateByVersion(rawUserAgents, humanStats) {
    const humanByName = new Map(humanStats.map(h => [h.name, h]));
    const byVersion = new Map();
    for (const row of rawUserAgents) {
        const version = firefoxMajorVersion(row.name);
        if (version === null) continue;
        const entry = byVersion.get(version) ?? { version, hit: 0, miss: 0, function: 0, error: 0, human: 0, total: 0 };
        entry.hit += row.hit;
        entry.miss += row.miss;
        entry.function += row['function'];
        entry.error += row.error;
        const h = humanByName.get(row.name);
        if (h) {
            entry.human += h.humanRequests;
            entry.total += h.totalRequests;
        }
        byVersion.set(version, entry);
    }
    return [...byVersion.values()];
}

// Human-traffic ratio for a version row. Rows with no traffic at all get the sentinel -1 so
// they sort as the lowest value and display as '–'.
function humanRatio(row) {
    return row.total > 0 ? row.human / row.total : -1;
}

// Sort keys for the Firefox Versions table — each maps a row to the numeric value to compare on,
// so "Firefox Version" sorts by the version number itself, never lexicographically on label text
// (which would put "Firefox 10" before "Firefox 9").
const SORT_VALUE = {
    version:  row => row.version,
    requests: row => resultTotal(row),
    human:    row => humanRatio(row),
};

export function sortVersions(versions, key, dir) {
    const getValue = SORT_VALUE[key];
    const sign = dir === 'asc' ? 1 : -1;
    return [...versions].sort((a, b) => sign * (getValue(a) - getValue(b)));
}

let currentVersions = [];
let sortKey = 'version';
let sortDir = 'desc';

function updateSortIndicators() {
    document.querySelectorAll('#tableVersions [data-sort-key]').forEach(th => {
        const active = th.dataset.sortKey === sortKey;
        th.classList.toggle('cf-sort-active', active);
        th.querySelector('.cf-sort-indicator').textContent = active ? (sortDir === 'asc' ? '▲' : '▼') : '';
    });
}

function renderVersionsTable() {
    updateSortIndicators();
    const tbody = document.getElementById('tbodyVersions');
    const legend = document.getElementById('versionBarLegend');
    if (!currentVersions.length) {
        tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">No data</td></tr>';
        legend.style.setProperty('display', 'none', 'important');
        return;
    }
    const rows = sortVersions(currentVersions, sortKey, sortDir);
    const maxTotal = Math.max(...currentVersions.map(resultTotal));
    tbody.innerHTML = rows.map((row, i) => {
        const ratio = humanRatio(row);
        const humanPct = ratio < 0 ? '–' : `${(ratio * 100).toFixed(1)}%`;
        return `
            <tr>
                <td class="text-muted">${i + 1}</td>
                <td class="font-monospace">Firefox ${row.version}</td>
                <td class="text-end">${resultTotal(row).toLocaleString()}</td>
                <td class="text-end">${humanPct}</td>
                <td>${stackedBar(row, maxTotal)}</td>
            </tr>`;
    }).join('');
    legend.style.removeProperty('display');
}

document.querySelectorAll('#tableVersions [data-sort-key]').forEach(th => {
    th.addEventListener('click', () => {
        const key = th.dataset.sortKey;
        sortDir = key === sortKey ? (sortDir === 'asc' ? 'desc' : 'asc') : 'desc';
        sortKey = key;
        renderVersionsTable();
    });
});

async function loadAllCharts() {
    const p = buildBaseParams({});

    Charts.loadChart(`firefox/result-types?${p}`,     d => Charts.pie('chartResultTypes', d, Charts.RESULT_TYPE_COLORS));
    Charts.loadChart(`firefox/countries?${p}`,        d => Charts.pie('chartCountries', d, null));
    Charts.loadChart(`firefox/uri-stems?${p}`,        d => Charts.horizontalStackedBar('chartUriStems', d));
    Charts.loadChart(`firefox/requests-per-day?${p}`, d => Charts.stackedBarByDay('chartRequestsPerDay', d));

    const tbody = document.getElementById('tbodyVersions');
    tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">Loading…</td></tr>';
    document.getElementById('versionBarLegend').style.setProperty('display', 'none', 'important');

    const [rawUserAgents, humanStats] = await Promise.all([
        fetch(`/api/firefox/user-agents?${p}`).then(r => r.json()),
        fetch(`/api/firefox/human-traffic?${p}`).then(r => r.json()),
    ]);
    const esrVersion = Number(readMeta('cf-firefox-esr'));
    renderMinVersionBanner('minFirefoxVersionBanner', 'Firefox', humanStats, undefined, [esrVersion]);

    currentVersions = aggregateByVersion(rawUserAgents, humanStats);
    renderVersionsTable();
}

loadAllCharts();
