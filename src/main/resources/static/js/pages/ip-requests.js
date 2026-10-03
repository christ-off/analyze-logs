'use strict';

import { escapeHtml } from '../utils.js';
import { getIpInfo } from '../ip-info.js';
import { initRefresh } from '../refresh.js';

export function init() {
    const el = document.getElementById('ipInfo');
    getIpInfo(el.dataset.ip)
        .then(info => {
            el.innerHTML = `${escapeHtml(info.org)} · ${escapeHtml(info.city)}, ${escapeHtml(info.country)} · ${escapeHtml(info.hostname)}`;
        })
        .catch(() => { el.textContent = 'lookup failed'; });
}

init();
initRefresh(() => location.reload());
