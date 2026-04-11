import { Charts } from './charts.js';

const from = document.querySelector('meta[name="cf-from"]').content;
const to   = document.querySelector('meta[name="cf-to"]').content;
const ua   = document.querySelector('meta[name="cf-ua"]').content;

const uaParams = new URLSearchParams({ ua, from: Charts.toDateParam(from), to: Charts.toDateParam(to) });
const p = uaParams.toString();

Charts.loadChart(`ua-detail/result-types?${p}`,     d => Charts.pie('chartResultTypes',          d, Charts.RESULT_TYPE_COLORS));
Charts.loadChart(`ua-detail/countries?${p}`,        d => Charts.pie('chartCountries',             d, null));
Charts.loadChart(`ua-detail/uri-stems?${p}`,        d => Charts.horizontalStackedBar('chartUriStems', d));
Charts.loadChart(`ua-detail/requests-per-day?${p}`, d => Charts.stackedBarByDay('chartRequestsPerDay', d));

const BAR_COLORS = {
    hit:      'rgba(40,167,69,0.8)',
    miss:     'rgba(54,162,235,0.8)',
    function: 'rgba(253,126,20,0.8)',
    redirect: 'rgba(111,66,193,0.8)',
    error:    'rgba(220,53,69,0.8)',
};

function stackedBar(row) {
    const total = row.hit + row.miss + row.function + row.redirect + row.error;
    if (total === 0) return '';
    return Object.entries(BAR_COLORS)
        .filter(([k]) => row[k] > 0)
        .map(([k, color]) => {
            const pct = (row[k] / total * 100).toFixed(2);
            return `<div style="width:${pct}%;background:${color};height:100%"></div>`;
        }).join('');
}

const data = await (await fetch(`/api/ua-detail/user-agents?${p}`)).json();
const tbody = document.getElementById('tbodyUserAgents');
if (!data.length) {
    tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-3">No data</td></tr>';
} else {
    const total = (row) => row.hit + row.miss + row.function + row.redirect + row.error;
    tbody.innerHTML = data.map((row, i) => `
            <tr>
                <td class="text-muted">${i + 1}</td>
                <td class="font-monospace small text-break">${escapeHtml(row.name ?? '(none)')}</td>
                <td class="text-end">${total(row).toLocaleString()}</td>
                <td><div style="display:flex;height:16px;border-radius:3px;overflow:hidden;width:100%">${stackedBar(row)}</div></td>
            </tr>`).join('');
    document.getElementById('uaBarLegend').style.removeProperty('display');
}

function escapeHtml(s) {
    return s.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;');
}