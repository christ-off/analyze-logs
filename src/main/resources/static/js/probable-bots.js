import { readMeta, buildBaseParams, initToggleBots } from './utils.js';

const from = readMeta('cf-from');
const to   = readMeta('cf-to');

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
                tbody.innerHTML = '<tr><td colspan="2" class="text-center text-muted">No probable bots found for the selected date range.</td></tr>';
                return;
            }

            tbody.innerHTML = data.map(bot => `
                <tr>
                    <td><code class="text-truncate d-inline-block" style="max-width: 500px;" title="${bot.name}">${bot.name}</code></td>
                    <td class="text-end">${bot.count.toLocaleString()}</td>
                </tr>
            `).join('');
        })
        .catch(e => {
            console.error('Failed to load probable bots:', e);
        });
}

initToggleBots(loadProbableBots);
loadProbableBots();