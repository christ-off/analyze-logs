'use strict';

import { buildBaseParams, escapeHtml, loadSimpleTable, uaRequestsUrl } from '../utils.js';
import { initRefresh } from '../refresh.js';

// Rows are already Filtered-only, so the count is the blocked-request count — no stacked bar.
// The raw UA string is shown in full (it identifies the app) — `text-break` wraps the long ones.
// The link is to /ua-requests (keyed by raw user_agent), not /ua-detail (keyed by classified ua_name):
// the row's identity is the raw user agent.
function blockedRow(r) {
    return `<tr>
        <td class="text-break small"><a href="${uaRequestsUrl(r.userAgent)}">${escapeHtml(r.userAgent)}</a></td>
        <td class="text-end">${r.count.toLocaleString()}</td>
    </tr>`;
}

export function loadBlockedTables() {
    const p = buildBaseParams({});
    loadSimpleTable(`/api/blocked-feed-readers?${p}`, 'blockedFeedReadersTable', 2, blockedRow,
        'No blocked feed readers from hosting/business ASNs in the selected date range.');
    loadSimpleTable(`/api/blocked-fediverse?${p}`, 'blockedFediverseTable', 2, blockedRow,
        'No blocked Fediverse apps from hosting/business ASNs in the selected date range.');
}

loadBlockedTables();
initRefresh(loadBlockedTables);
