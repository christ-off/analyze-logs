'use strict';

import { getIpInfo } from './ip-info.js';

// Each helper knows how to recognize its provider from ipinfo.io data and how to
// turn a set of request rows into a report for that provider's abuse form.
// Add more entries here as other "abuse report helpers" are built.
const HELPERS = [
    {
        id: 'google',
        label: 'Report to Google',
        formUrl: 'https://support.google.com/code/contact/cloud_platform_report',
        matches: (info) => /google/i.test(info?.org || ''),
        buildReport: buildGoogleReportText,
    },
    {
        id: 'microsoft',
        label: 'Report to Microsoft',
        formUrl: 'https://msrc.microsoft.com/report/abuse?ThreatType=IpAddress&IncidentType=BruteForce',
        matches: (info) => /microsoft/i.test(info?.org || ''),
        buildReport: buildPlainReportText,
    },
];

export function findHelpers(info) {
    return HELPERS.filter(h => h.matches(info));
}

// Google's abuse-details field caps input at this length.
export const MAX_REPORT_CHARS = 1000;

function formatRequestLine(entry) {
    const ts = (entry.timestamp || '?').replace('T', ' ').replace(/\.\d+Z?$/, '').replace(/Z$/, '');
    return `${ts} UTC  ${entry.ip}  ${entry.uriStem}`;
}

// Keeps the pasted text to just what the abuse team needs to look up the traffic:
// one "date time UTC · source IP · URI" line per request, oldest-selection-order first,
// dropped from the end (with a note) if the combined text would exceed the form's limit.
function buildGoogleReportText(entries) {
    const lines = entries.map(formatRequestLine);
    let kept = lines.length;
    let text = lines.join('\n') + '\n';
    while (text.length > MAX_REPORT_CHARS && kept > 0) {
        kept--;
        const omitted = lines.length - kept;
        const note = `…and ${omitted} more (Google's form is limited to ${MAX_REPORT_CHARS} characters)`;
        text = (kept > 0 ? lines.slice(0, kept).join('\n') + '\n' : '') + note + '\n';
    }
    return { text, omitted: lines.length - kept };
}

// MSRC's abuse form has no known single-field length cap (IPs/URLs go into their own
// fields), so this just lists the requests with no truncation.
function buildPlainReportText(entries) {
    return { text: entries.map(formatRequestLine).join('\n') + '\n', omitted: 0 };
}

export async function copyAndOpen(text, url) {
    try {
        await navigator.clipboard.writeText(text);
    } catch {
        // best-effort clipboard write; the form still opens either way
    }
    window.open(url, '_blank', 'noopener');
}

function readUa() {
    return document.querySelector('meta[name="cf-ua"]')?.content || '';
}

export function entryFromRow(row) {
    return {
        timestamp: row.dataset.timestamp,
        ip: row.querySelector('.ip-cell')?.dataset.ip,
        uriStem: row.dataset.uri,
        resultType: row.dataset.result,
        scStatus: row.dataset.status,
        country: row.dataset.country,
        ua: readUa(),
    };
}

export function initAbuseReportButtons(root = document) {
    root.addEventListener('ip-info:loaded', (e) => {
        const { cell, info } = e.detail;
        const helpers = findHelpers(info);
        if (helpers.length === 0) return;
        const block = cell.querySelector('.ip-info-block');
        const row = cell.closest('tr');
        if (!block || !row) return;

        helpers.forEach(helper => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'btn btn-sm btn-outline-danger mt-1 d-block';
            btn.textContent = helper.label;
            btn.addEventListener('click', (ev) => {
                ev.stopPropagation();
                const entry = { ...entryFromRow(row), info };
                const { text, omitted } = helper.buildReport([entry]);
                if (omitted > 0) alert(`Report truncated: Google's form is limited to ${MAX_REPORT_CHARS} characters.`);
                copyAndOpen(text, helper.formUrl);
            });
            block.appendChild(btn);
        });
    });
}

export function initBulkAbuseReport(root = document) {
    const bulkBtn = root.querySelector('#bulkReportBtn');
    if (!bulkBtn) return;
    const selectAll = root.querySelector('#selectAllRows');

    const checkboxes = () => Array.from(root.querySelectorAll('.row-select'));

    const updateToolbar = () => {
        const n = checkboxes().filter(c => c.checked).length;
        bulkBtn.classList.toggle('d-none', n === 0);
        bulkBtn.textContent = n > 0 ? `Report ${n} selected` : 'Report selected';
    };

    checkboxes().forEach(cb => cb.addEventListener('change', updateToolbar));
    selectAll?.addEventListener('change', () => {
        checkboxes().forEach(cb => { cb.checked = selectAll.checked; });
        updateToolbar();
    });
    // Fired by initSelectSameIp (and any future code) after checking boxes programmatically,
    // since setting .checked directly doesn't dispatch a native 'change' event.
    root.addEventListener('row-select:refresh', updateToolbar);

    bulkBtn.addEventListener('click', async () => {
        const selected = checkboxes().filter(c => c.checked);
        if (selected.length === 0) return;

        const entries = await Promise.all(selected.map(async cb => {
            const row = cb.closest('tr');
            const entry = entryFromRow(row);
            entry.info = await getIpInfo(entry.ip);
            return entry;
        }));

        // Group by the first helper each entry matches, so a mixed selection (e.g. some
        // Google IPs, some Microsoft IPs) doesn't get silently merged into one report.
        const groups = new Map();
        const unmatched = [];
        entries.forEach(entry => {
            const helper = findHelpers(entry.info)[0];
            if (!helper) { unmatched.push(entry); return; }
            if (!groups.has(helper.id)) groups.set(helper.id, { helper, entries: [] });
            groups.get(helper.id).entries.push(entry);
        });

        if (groups.size === 0) {
            alert('None of the selected rows originate from a provider with a known abuse-report form.');
            return;
        }
        if (groups.size > 1) {
            const names = Array.from(groups.values()).map(g => `${g.entries.length} ${g.helper.label.replace('Report to ', '')}`).join(', ');
            alert(`Selected rows span multiple providers (${names}). Select rows for one provider at a time.`);
            return;
        }
        const { helper, entries: matched } = groups.values().next().value;
        if (unmatched.length > 0) {
            alert(`${unmatched.length} selected row(s) do not match ${helper.label.replace('Report to ', '')} and were left out of the report.`);
        }
        const { text, omitted } = helper.buildReport(matched);
        if (omitted > 0) {
            alert(`${omitted} more request(s) left out: ${helper.label.replace('Report to ', '')}'s form is limited to ${MAX_REPORT_CHARS} characters.`);
        }
        copyAndOpen(text, helper.formUrl);
    });

    updateToolbar();
}

// Independent of any provider lookup: lets a row's checkbox selection be extended
// to every other row sharing the same client IP, in one click.
export function initSelectSameIp(root = document) {
    root.querySelectorAll('.ip-cell[data-ip]').forEach(cell => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'btn btn-link btn-sm p-0 ms-1';
        btn.textContent = '⊕';
        btn.title = 'Select all rows with this IP';
        btn.addEventListener('click', (ev) => {
            ev.stopPropagation();
            const ip = cell.dataset.ip;
            root.querySelectorAll('.ip-cell[data-ip="' + ip + '"]').forEach(match => {
                const checkbox = match.closest('tr')?.querySelector('.row-select');
                if (checkbox) checkbox.checked = true;
            });
            root.dispatchEvent(new CustomEvent('row-select:refresh', { bubbles: true }));
        });
        cell.appendChild(btn);
    });
}
