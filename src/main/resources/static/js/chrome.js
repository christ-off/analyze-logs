import { Charts } from './charts.js';
import { buildBaseParams, initToggleBots, resultTotal, stackedBar, minVersionWithHumanTraffic } from './utils.js';

// Extract the Chrome major version from a raw user_agent string, e.g. "...Chrome/120.0.0.0..." -> 120.
export function chromeMajorVersion(rawUa) {
    const m = rawUa.match(/Chrome\/(\d+)/);
    return m ? Number(m[1]) : null;
}

// Fold per-raw-UA result-type sums and human-traffic stats (one row per raw user_agent string,
// across every OS) down into one row per Chrome major version, ordered by total requests desc.
export function aggregateByVersion(rawUserAgents, humanStats) {
    const humanByName = new Map(humanStats.map(h => [h.name, h]));
    const byVersion = new Map();
    for (const row of rawUserAgents) {
        const version = chromeMajorVersion(row.name);
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
    return [...byVersion.values()].sort((a, b) => resultTotal(b) - resultTotal(a));
}

function updateMinVersionBanner(humanStats) {
    const banner = document.getElementById('minChromeVersionBanner');
    if (!banner) return;
    const minVersion = minVersionWithHumanTraffic(humanStats, 'Chrome');
    if (minVersion === null) {
        banner.classList.add('d-none');
    } else {
        banner.textContent = `Min Chrome version with requests from human IPs: ${minVersion}`;
        banner.classList.remove('d-none');
    }
}

async function loadAllCharts() {
    const p = buildBaseParams({});

    Charts.loadChart(`chrome/result-types?${p}`,     d => Charts.pie('chartResultTypes', d, Charts.RESULT_TYPE_COLORS));
    Charts.loadChart(`chrome/countries?${p}`,        d => Charts.pie('chartCountries', d, null));
    Charts.loadChart(`chrome/uri-stems?${p}`,        d => Charts.horizontalStackedBar('chartUriStems', d));
    Charts.loadChart(`chrome/requests-per-day?${p}`, d => Charts.stackedBarByDay('chartRequestsPerDay', d));

    const tbody = document.getElementById('tbodyVersions');
    tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">Loading…</td></tr>';
    document.getElementById('versionBarLegend').style.setProperty('display', 'none', 'important');

    const [rawUserAgents, humanStats] = await Promise.all([
        fetch(`/api/chrome/user-agents?${p}`).then(r => r.json()),
        fetch(`/api/chrome/human-traffic?${p}`).then(r => r.json()),
    ]);
    updateMinVersionBanner(humanStats);

    const versions = aggregateByVersion(rawUserAgents, humanStats);
    if (versions.length) {
        const maxTotal = Math.max(...versions.map(resultTotal));
        tbody.innerHTML = versions.map((row, i) => {
            const humanPct = row.total > 0 ? `${(100 * row.human / row.total).toFixed(1)}%` : '–';
            return `
            <tr>
                <td class="text-muted">${i + 1}</td>
                <td class="font-monospace">Chrome ${row.version}</td>
                <td class="text-end">${resultTotal(row).toLocaleString()}</td>
                <td class="text-end">${humanPct}</td>
                <td>${stackedBar(row, maxTotal)}</td>
            </tr>`;
        }).join('');
        document.getElementById('versionBarLegend').style.removeProperty('display');
    } else {
        tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">No data</td></tr>';
    }
}

initToggleBots(loadAllCharts);
