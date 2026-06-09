'use strict';

import { escapeHtml } from './utils.js';

export function initIpLookup(root = document) {
    root.querySelectorAll('.ip-cell').forEach(cell => {
        cell.style.cursor = 'pointer';
        cell.addEventListener('click', function onClick() {
            cell.removeEventListener('click', onClick);
            const ip = cell.dataset.ip;
            cell.insertAdjacentHTML('beforeend', '<span class="spinner-border spinner-border-sm ms-1" role="status"></span>');

            fetch('/api/ip-info/' + encodeURIComponent(ip))
                .then(r => r.json())
                .then(info => {
                    cell.querySelector('.spinner-border')?.remove();
                    cell.insertAdjacentHTML('beforeend',
                        `<div class="text-muted small font-monospace mt-1">${escapeHtml(info.org)} · ${escapeHtml(info.city)}, ${escapeHtml(info.country)}<br>${escapeHtml(info.hostname)}</div>`
                    );
                })
                .catch(() => {
                    cell.querySelector('.spinner-border')?.remove();
                    cell.insertAdjacentHTML('beforeend', '<div class="text-muted small mt-1">lookup failed</div>');
                });
        });
    });
}
