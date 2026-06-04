import { Charts } from './charts.js';
import { readMeta, escapeHtml, buildBaseParams, initToggleBots, resultTotal, uaRequestsUrl } from './utils.js';

const from = readMeta('cf-from');
const to   = readMeta('cf-to');
const ua   = readMeta('cf-ua');

const CHART_IDS = ['chartResultTypes', 'chartCountries', 'chartUriStems', 'chartRequestsPerDay'];

async function loadAllCharts() {
    CHART_IDS.forEach(id => Chart.getChart(id)?.destroy());
    const p = buildBaseParams({ ua });

    Charts.loadChart(`ua-detail/result-types?${p}`,     d => Charts.pie('chartResultTypes',          d, Charts.RESULT_TYPE_COLORS));
    Charts.loadChart(`ua-detail/countries?${p}`,        d => Charts.pie('chartCountries',             d, null));
    Charts.loadChart(`ua-detail/uri-stems?${p}`,        d => Charts.horizontalStackedBar('chartUriStems', d));
    Charts.loadChart(`ua-detail/requests-per-day?${p}`, d => Charts.stackedBarByDay('chartRequestsPerDay', d));

    const tbody = document.getElementById('tbodyUserAgents');
    tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-3">Loading…</td></tr>';
    document.getElementById('uaBarLegend').style.setProperty('display', 'none', 'important');

    const data = await (await fetch(`/api/ua-detail/user-agents?${p}`)).json();
    if (data.length) {
        tbody.innerHTML = data.map((row, i) => `
            <tr>
                <td class="text-muted">${i + 1}</td>
                <td class="font-monospace small text-break">
                    ${row.name
                        ? `<a href="${uaRequestsUrl(row.name)}">${escapeHtml(row.name)}</a>`
                        : '(none)'}
                </td>
                <td class="text-end">${resultTotal(row).toLocaleString()}</td>
                <td><div style="display:flex;height:16px;border-radius:3px;overflow:hidden;width:100%">${stackedBar(row)}</div></td>
            </tr>`).join('');
        document.getElementById('uaBarLegend').style.removeProperty('display');
    } else {
        tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-3">No data</td></tr>';
    }
}

const BAR_COLORS = {
    hit:      Charts.COLORS.green,
    miss:     Charts.COLORS.blue,
    function: Charts.COLORS.orange,
    error:    Charts.COLORS.red,
};

function stackedBar(row) {
    const total = resultTotal(row);
    if (total === 0) return '';
    return Object.entries(BAR_COLORS)
        .filter(([k]) => row[k] > 0)
        .map(([k, color]) => {
            const pct = (row[k] / total * 100).toFixed(2);
            return `<div style="width:${pct}%;background:${color};height:100%"></div>`;
        }).join('');
}

initToggleBots(loadAllCharts);
