'use strict';

import { buildBaseParams, escapeHtml, loadSimpleTable } from '../utils.js';

export function loadUris() {
    const p = buildBaseParams({});
    loadSimpleTable('/api/zip-requests/uris?' + p, 'zipRequestsTable', 2, u => `<tr>
        <td><code>${escapeHtml(u.name)}</code></td>
        <td class="text-end">${u.count.toLocaleString()}</td>
    </tr>`, 'No zip requests found for the selected date range.', (_, data) => {
        const countEl = document.getElementById('zipRequestsCount');
        if (countEl) countEl.textContent = `(${data.length.toLocaleString()} URIs, sorted by count)`;
    });
}

loadUris();
