'use strict';

import { buildBaseParams, escapeHtml, loadSimpleTable, resultTotal, stackedBar } from '../utils.js';

const COLS = 3;
let maxTotal;

loadSimpleTable('/api/zip-requests/uris?' + buildBaseParams({}), 'zipRequestsTable', COLS, (u, i, rows) => {
    if (i === 0) maxTotal = Math.max(...rows.map(resultTotal));
    return `<tr>
    <td><code>${escapeHtml(u.name)}</code></td>
    <td class="text-end">${resultTotal(u).toLocaleString()}</td>
    <td class="align-middle px-2">${stackedBar(u, maxTotal)}</td>
</tr>`;
}, 'No archive requests found for the selected date range.', (_, data) => {
    document.getElementById('zipRequestsCount').textContent = `(${data.length.toLocaleString()} URIs, sorted by count)`;
    document.getElementById('zipRequestsLegend')?.style.removeProperty('display');
});
