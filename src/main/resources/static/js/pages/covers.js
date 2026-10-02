'use strict';

import { Charts } from '../charts.js';
import { buildBaseParams, detailUrl, escapeHtml, loadSimpleTable, resultTotal, stackedBar, uaRequestsUrl } from '../utils.js';
import { initRefresh } from '../refresh.js';

export function loadCoverCharts() {
    const p = buildBaseParams({});
    Charts.loadChart(`covers/user-agents?${p}`, data => Charts.horizontalStackedBar('chartCoverUas', data,
        d => detailUrl('/ua-detail', { ua: d.name })));
    loadSimpleTable(`/api/covers/user-agent-table?${p}`, 'coverUaTable', 4, r => `<tr>
        <td class="text-break small"><a href="${uaRequestsUrl(r.name)}">${escapeHtml(r.name)}</a></td>
        <td class="text-end">${resultTotal(r).toLocaleString()}</td>
        <td class="text-end">${r.human.toLocaleString()}</td>
        <td class="align-middle px-2">${stackedBar(r, null)}</td>
    </tr>`, 'No cover requests found for the selected date range.',
    () => document.getElementById('coverUaLegend')?.style.removeProperty('display'));
    Charts.loadChart(`covers/referer-split?${p}`, data => Charts.pie('chartCoverReferer', data, null));
    Charts.loadChart(`covers/requests-per-day?${p}`, data => Charts.stackedBarByDay('chartRequestsPerDay', data));
}

loadCoverCharts();
initRefresh(loadCoverCharts);
