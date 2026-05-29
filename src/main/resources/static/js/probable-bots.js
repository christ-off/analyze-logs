'use strict';

import { buildBaseParams, initToggleBots } from './utils.js';

const SEGMENTS = [
    { key: 'hit',      label: 'Hit',      color: 'rgba(40, 167, 69, 0.8)'  },
    { key: 'miss',     label: 'Miss',     color: 'rgba(54, 162, 235, 0.8)' },
    { key: 'function', label: 'Filtered', color: 'rgba(253, 126, 20, 0.8)' },
    { key: 'error',    label: 'Error',    color: 'rgba(220, 53, 69, 0.8)'  },
];

function stackedBar(bot, maxTotal) {
    const total = bot.hit + bot.miss + bot.function + bot.error;
    const pct = total / maxTotal * 100;
    const segments = SEGMENTS
        .filter(s => bot[s.key] > 0)
        .map(s => {
            const w = (bot[s.key] / total * pct).toFixed(2);
            return `<div style="width:${w}%;background:${s.color};height:100%" title="${s.label}: ${bot[s.key].toLocaleString()}"></div>`;
        })
        .join('');
    return `<div style="display:flex;height:1.1em;width:100%;border-radius:2px;overflow:hidden">${segments}</div>`;
}

function loadProbableBots() {
    const p = buildBaseParams({});
    const toggle = document.getElementById('toggleBots');
    const excludeBots = toggle ? toggle.checked : false;
    const params = new URLSearchParams(p);
    params.append('excludeBots', excludeBots);

    fetch('/api/probable-bots?' + params.toString())
        .then(r => {
            if (!r.ok) throw new Error(`HTTP ${r.status}`);
            return r.json();
        })
        .then(data => {
            const tbody = document.querySelector('#probableBotsTable');
            if (!tbody) return;

            if (data.length === 0) {
                tbody.innerHTML = '<tr><td colspan="3" class="text-center text-muted">No probable bots found for the selected date range.</td></tr>';
                return;
            }

            const maxTotal = Math.max(...data.map(b => b.hit + b.miss + b.function + b.error));

            tbody.innerHTML = data.map(bot => {
                const total = bot.hit + bot.miss + bot.function + bot.error;
                return `<tr>
                    <td><code>${bot.name}</code></td>
                    <td class="text-end">${total.toLocaleString()}</td>
                    <td class="align-middle px-2">${stackedBar(bot, maxTotal)}</td>
                </tr>`;
            }).join('');
        })
        .catch(e => console.error('Failed to load probable bots:', e));
}

initToggleBots(loadProbableBots);
loadProbableBots();
