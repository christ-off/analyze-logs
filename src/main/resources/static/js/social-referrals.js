'use strict';

import { buildBaseParams, escapeHtml, formatTimestamp, stackedBar, resultTotal, uaRequestsUrl } from './utils.js';
import { initRefresh } from './refresh.js';

// The template owns which networks are shown and in what order; each section tags its tbody.
function sections() {
    return document.querySelectorAll('#socialReferralsSections tbody[data-network]');
}

function renderRow(req) {
    return `<tr>
        <td class="text-nowrap">${escapeHtml(formatTimestamp(req.timestamp))}</td>
        <td class="text-break small"><a href="${uaRequestsUrl(req.userAgent)}">${escapeHtml(req.userAgent)}</a></td>
        <td class="font-monospace small text-truncate" style="max-width:320px" title="${escapeHtml(req.uriStem)}">${escapeHtml(req.uriStem)}</td>
        <td>${escapeHtml(req.country)}</td>
    </tr>`;
}

function renderNetwork(tbody, requests) {
    tbody.classList.remove('text-muted');
    tbody.innerHTML = requests.length === 0
        ? '<tr><td colspan="4" class="text-center text-muted py-3">No hits from this network in the selected date range.</td></tr>'
        : requests.map(renderRow).join('');
}

function renderResultTypes(data) {
    document.querySelectorAll('#socialReferralsSections [data-result-network]').forEach(el => {
        const row = data[el.dataset.resultNetwork];
        el.innerHTML = row && resultTotal(row) > 0
            ? stackedBar(row, null) + '<div class="small text-muted mt-1">'
                + `Hit ${row.hit.toLocaleString()} · Miss ${row.miss.toLocaleString()} · `
                + `Filtered ${row['function'].toLocaleString()} · Error ${row.error.toLocaleString()}</div>`
            : '';
    });
}

export function loadSocialReferrals() {
    const p = buildBaseParams({});
    fetch('/api/social-networks/result-types?' + p)
        .then(r => r.json())
        .then(renderResultTypes)
        .catch(() => {});
    fetch('/api/social-networks?' + p)
        .then(r => r.json())
        .then(data => {
            sections().forEach(tbody => renderNetwork(tbody, data[tbody.dataset.network] || []));
        })
        .catch(() => {
            sections().forEach(tbody => {
                tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-3">Failed to load data.</td></tr>';
            });
        });
}

loadSocialReferrals();
initRefresh(loadSocialReferrals);
