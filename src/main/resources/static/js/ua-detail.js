import { Charts } from './charts.js';
import { readMeta, escapeHtml, buildBaseParams, initToggleBots, resultTotal, stackedBar, uaRequestsUrl } from './utils.js';

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
        const maxTotal = Math.max(...data.map(resultTotal));
        tbody.innerHTML = data.map((row, i) => `
            <tr>
                <td class="text-muted">${i + 1}</td>
                <td class="font-monospace small text-break">
                    ${row.name
                        ? `<a href="${uaRequestsUrl(row.name)}">${escapeHtml(row.name)}</a>`
                        : '(none)'}
                </td>
                <td class="text-end">${resultTotal(row).toLocaleString()}</td>
                <td>${stackedBar(row, maxTotal)}</td>
            </tr>`).join('');
        document.getElementById('uaBarLegend').style.removeProperty('display');
    } else {
        tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-3">No data</td></tr>';
    }
}


initToggleBots(loadAllCharts);
