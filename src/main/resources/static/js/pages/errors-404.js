'use strict';

import { buildBaseParams, escapeHtml, loadSimpleTable } from '../utils.js';

export function loadUris() {
    const p = buildBaseParams({});
    loadSimpleTable('/api/errors-404/uris?' + p, 'errors404Table', 2, u => `<tr>
        <td><code>${escapeHtml(u.name)}</code></td>
        <td class="text-end">${u.count.toLocaleString()}</td>
    </tr>`, 'No 404s or errors found for the selected date range.', (_, data) => {
        const countEl = document.getElementById('errors404Count');
        if (countEl) countEl.textContent = `(${data.length.toLocaleString()} URIs, sorted by count)`;
    });
}

loadUris();
