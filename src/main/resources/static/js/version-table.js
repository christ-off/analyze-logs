import { resultTotal, stackedBar } from './utils.js';

// Fold per-raw-UA result-type sums and human-traffic stats (one row per raw user_agent string,
// across every OS) down into one row per browser major version. Unordered — the table sorts.
export function aggregateByVersion(rawUserAgents, humanStats, majorVersionFn) {
    const humanByName = new Map(humanStats.map(h => [h.name, h]));
    const byVersion = new Map();
    for (const row of rawUserAgents) {
        const version = majorVersionFn(row.name);
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

// Sort keys for a Versions table — each maps a row to the numeric value to compare on, so
// "Version" sorts by the version number itself, never lexicographically on label text (which
// would put "10" before "9").
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

// Wires up the #tableVersions / #tbodyVersions / #versionBarLegend markup shared by the
// Chrome/Edge/Firefox version dashboards: sortable header clicks and rendering the current rows.
export function createVersionTable(browserLabel) {
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

    function render() {
        updateSortIndicators();
        const tbody = document.getElementById('tbodyVersions');
        const legend = document.getElementById('versionBarLegend');
        if (!tbody || !legend) return;
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
                    <td class="font-monospace">${browserLabel} ${row.version}</td>
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
            render();
        });
    });

    return {
        setVersions(versions) {
            currentVersions = versions;
            render();
        },
    };
}
