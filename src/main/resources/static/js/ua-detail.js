import { Charts } from './charts.js';
import { readMeta, escapeHtml, buildBaseParams, initToggleBots, resultTotal, stackedBar, uaRequestsUrl } from './utils.js';

const ua   = readMeta('cf-ua');
const CHROME_DESKTOP_UAS = new Set(['Chrome / Windows', 'Chrome / Linux', 'Chrome / macOS']);

// Lowest Chrome major version, across raw UA strings sharing that version, with
// any requests from "Probable human" IPs — versions below it look spoofed (bots
// declaring an old/fake Chrome version never show human evidence).
export function minChromeVersionWithHumanTraffic(humanStats) {
    const totalsByVersion = new Map();
    for (const h of humanStats) {
        const m = h.name.match(/Chrome\/(\d+)/);
        if (!m) continue;
        const version = Number(m[1]);
        const entry = totalsByVersion.get(version) ?? { human: 0, total: 0 };
        entry.human += h.humanRequests;
        entry.total += h.totalRequests;
        totalsByVersion.set(version, entry);
    }
    let min = null;
    for (const [version, { human }] of totalsByVersion) {
        if (human > 0 && (min === null || version < min)) min = version;
    }
    return min;
}

function updateChromeHumanVersionBanner(humanStats) {
    const banner = document.getElementById('chromeMinHumanVersionBanner');
    if (!banner) return;
    const minVersion = CHROME_DESKTOP_UAS.has(ua) ? minChromeVersionWithHumanTraffic(humanStats) : null;
    if (minVersion === null) {
        banner.classList.add('d-none');
    } else {
        banner.textContent = `Min Chrome version with requests from human IPs: ${minVersion}`;
        banner.classList.remove('d-none');
    }
}

async function loadAllCharts() {
    const p = buildBaseParams({ ua });

    Charts.loadChart(`ua-detail/result-types?${p}`,     d => Charts.pie('chartResultTypes',          d, Charts.RESULT_TYPE_COLORS));
    Charts.loadChart(`ua-detail/countries?${p}`,        d => Charts.pie('chartCountries',             d, null));
    Charts.loadChart(`ua-detail/uri-stems?${p}`,        d => Charts.horizontalStackedBar('chartUriStems', d));
    Charts.loadChart(`ua-detail/requests-per-day?${p}`, d => Charts.stackedBarByDay('chartRequestsPerDay', d));

    const tbody = document.getElementById('tbodyUserAgents');
    tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">Loading…</td></tr>';
    document.getElementById('uaBarLegend').style.setProperty('display', 'none', 'important');

    const [data, humanStats] = await Promise.all([
        fetch(`/api/ua-detail/user-agents?${p}`).then(r => r.json()),
        fetch(`/api/ua-detail/human-traffic?${p}`).then(r => r.json()),
    ]);
    const humanByName = new Map(humanStats.map(h => [h.name, h]));
    updateChromeHumanVersionBanner(humanStats);
    if (data.length) {
        const maxTotal = Math.max(...data.map(resultTotal));
        tbody.innerHTML = data.map((row, i) => {
            const h = humanByName.get(row.name);
            const humanPct = h && h.totalRequests > 0 ? `${(100 * h.humanRequests / h.totalRequests).toFixed(1)}%` : '–';
            return `
            <tr>
                <td class="text-muted">${i + 1}</td>
                <td class="font-monospace small text-break">
                    ${row.name
                        ? `<a href="${uaRequestsUrl(row.name)}">${escapeHtml(row.name)}</a>`
                        : '(none)'}
                </td>
                <td class="text-end">${resultTotal(row).toLocaleString()}</td>
                <td class="text-end">${humanPct}</td>
                <td>${stackedBar(row, maxTotal)}</td>
            </tr>`;
        }).join('');
        document.getElementById('uaBarLegend').style.removeProperty('display');
    } else {
        tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">No data</td></tr>';
    }
}


initToggleBots(loadAllCharts);
