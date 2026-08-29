'use strict';

import { escapeHtml } from './utils.js';

let cache = new Map();

export function resetIpInfoCache() {
    cache = new Map();
}

export function getIpInfo(ip) {
    if (!cache.has(ip)) {
        cache.set(ip, fetch('/api/ip-info/' + encodeURIComponent(ip)).then(r => r.json()));
    }
    return cache.get(ip);
}

export function initIpLookup(root = document) {
    resetIpInfoCache();
    root.querySelectorAll('.ip-cell').forEach(cell => {
        cell.style.cursor = 'pointer';
        cell.addEventListener('click', function onClick() {
            cell.removeEventListener('click', onClick);
            const ip = cell.dataset.ip;
            cell.insertAdjacentHTML('beforeend', '<span class="spinner-border spinner-border-sm ms-1" role="status"></span>');

            getIpInfo(ip)
                .then(info => {
                    cell.querySelector('.spinner-border')?.remove();
                    cell.insertAdjacentHTML('beforeend',
                        `<div class="ip-info-block text-muted small font-monospace mt-1">${escapeHtml(info.org)} · ${escapeHtml(info.city)}, ${escapeHtml(info.country)}<br>${escapeHtml(info.hostname)}</div>`
                    );
                    cell.dispatchEvent(new CustomEvent('ip-info:loaded', { bubbles: true, detail: { cell, ip, info } }));
                })
                .catch(() => {
                    cell.querySelector('.spinner-border')?.remove();
                    cell.insertAdjacentHTML('beforeend', '<div class="text-muted small mt-1">lookup failed</div>');
                });
        });
    });
}
