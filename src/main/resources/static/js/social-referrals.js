'use strict';

import { buildBaseParams, detailUrl, escapeHtml, stackedBar } from './utils.js';

const NETWORKS = ['WhatsApp', 'Facebook', 'Discord', 'Twitter/X'];

function containerId(network) {
    return 'sr-' + network.replaceAll('/', '');
}

function formatTimestamp(iso) {
    return (iso || '?').replace('T', ' ').replace(/\.\d+Z?$/, '').replace(/Z$/, '');
}

function shortUa(name) {
    return name.length > 60 ? name.slice(0, 57) + '…' : name;
}

function renderRow(req) {
    return `<tr>
        <td class="text-nowrap">${escapeHtml(formatTimestamp(req.timestamp))}</td>
        <td><a href="${detailUrl('/ua-detail', { ua: req.uaName })}" title="${escapeHtml(req.userAgent)}">${escapeHtml(shortUa(req.userAgent))}</a></td>
        <td class="font-monospace small text-truncate" style="max-width:320px" title="${escapeHtml(req.uriStem)}">${escapeHtml(req.uriStem)}</td>
        <td>${escapeHtml(req.country)}</td>
        <td class="align-middle" style="width:90px">${stackedBar(req, null)}</td>
    </tr>`;
}

function renderNetwork(network, requests) {
    const tbody = document.getElementById(containerId(network));
    if (!tbody) return;
    tbody.classList.remove('text-muted');
    tbody.innerHTML = requests.length === 0
        ? '<tr><td colspan="5" class="text-center text-muted py-3">No hits from this network in the selected date range.</td></tr>'
        : requests.map(renderRow).join('');
}

export function loadSocialReferrals() {
    const p = buildBaseParams({});
    fetch('/api/social-networks?' + p)
        .then(r => r.json())
        .then(data => {
            NETWORKS.forEach(network => renderNetwork(network, data[network] || []));
        })
        .catch(() => {
            NETWORKS.forEach(network => {
                const tbody = document.getElementById(containerId(network));
                if (tbody) tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">Failed to load data.</td></tr>';
            });
        });
}

loadSocialReferrals();
