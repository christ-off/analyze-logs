'use strict';

import { Charts } from './charts.js';

export function readMeta(name) {
    return document.querySelector(`meta[name="${name}"]`).content;
}

export function escapeHtml(s) {
    return s.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
}

export function buildBaseParams(extra) {
    const from = readMeta('cf-from');
    const to   = readMeta('cf-to');
    const p = new URLSearchParams({ ...extra, from: Charts.toDateParam(from), to: Charts.toDateParam(to) });
    const toggleEl = document.getElementById('toggleBots');
    if (toggleEl?.checked) p.set('excludeBots', 'true');
    return p.toString();
}

export function initToggleBots(loadFn) {
    const toggleEl = document.getElementById('toggleBots');
    if (toggleEl) {
        toggleEl.checked = localStorage.getItem('excludeBots') === 'true';
        toggleEl.addEventListener('change', () => {
            localStorage.setItem('excludeBots', String(toggleEl.checked));
            loadFn();
        });
    }
    loadFn();
}
