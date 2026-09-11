'use strict';

import { buildBaseParams, escapeHtml, resultTotal, stackedBar, uaRequestsUrl } from './utils.js';

function formatTimestamp(iso) {
    return (iso || '?').replace('T', ' ').replace(/\.\d+Z?$/, '').replace(/Z$/, '');
}

function shortUa(name) {
    return name.length > 40 ? name.slice(0, 37) + '…' : name;
}

function renderUrlUserAgent(ua) {
    return `<a class="badge rounded-pill text-bg-secondary text-decoration-none me-1 mb-1"
               href="${uaRequestsUrl(ua)}" title="${escapeHtml(ua)}">${escapeHtml(shortUa(ua))}</a>`;
}

function renderUrlRow(url, maxTotal) {
    return `<tr>
        <td class="font-monospace small">${escapeHtml(url.name)}</td>
        <td>${url.userAgents.map(renderUrlUserAgent).join('')}</td>
        <td class="text-end">${resultTotal(url).toLocaleString()}</td>
        <td class="align-middle px-2" style="width:22%">${stackedBar(url, maxTotal)}</td>
    </tr>`;
}

function renderEntry(entry) {
    const maxTotal = entry.urls.length ? Math.max(...entry.urls.map(resultTotal)) : null;
    return `<div class="bt-entry pb-4 mb-4 border-bottom">
        <div class="d-flex flex-wrap align-items-baseline gap-2 mb-2">
            <span class="font-monospace fw-semibold fs-5">${escapeHtml(entry.ip)}</span>
            <span class="badge text-bg-warning">${entry.userAgents.length} identities</span>
            <span class="text-muted small ms-auto">
                last seen ${formatTimestamp(entry.lastSeen)} &middot; first seen ${formatTimestamp(entry.firstSeen)}
            </span>
        </div>
        <table class="table table-sm mb-0">
            <thead class="table-dark">
                <tr>
                    <th>URL</th>
                    <th>User Agents</th>
                    <th class="text-end">Requests</th>
                    <th>Hit / Miss / Filtered / Error</th>
                </tr>
            </thead>
            <tbody>${entry.urls.map(u => renderUrlRow(u, maxTotal)).join('')}</tbody>
        </table>
    </div>`;
}

export function loadIdentityShifts() {
    const p = buildBaseParams({});
    fetch('/api/identity-shifts?' + p)
        .then(r => r.json())
        .then(data => {
            const container = document.getElementById('identityShiftsList');
            if (!container) return;
            container.innerHTML = data.length === 0
                ? '<div class="text-muted text-center py-3">No face dancers found for the selected date range.</div>'
                : data.map(renderEntry).join('');
        })
        .catch(() => {
            const container = document.getElementById('identityShiftsList');
            if (container) container.innerHTML = '<div class="text-muted text-center py-3">Failed to load data.</div>';
        });
}

loadIdentityShifts();
