import { beforeEach, describe, it, expect, vi } from 'vitest';

async function flushPromises() {
    for (let i = 0; i < 10; i++) await Promise.resolve();
}

const GOOGLE_INFO = {
    ip: '34.73.59.67',
    hostname: '67.59.73.34.bc.googleusercontent.com',
    org: 'AS396982 Google LLC',
    city: 'North Charleston',
    country: 'US',
};
const OTHER_INFO = { ip: '1.2.3.4', hostname: 'host.example.com', org: 'AS1 Acme', city: 'Paris', country: 'FR' };

let abuseReport;
beforeEach(async () => {
    vi.resetModules();
    document.body.innerHTML = '<meta name="cf-ua" content="TestBot/1.0">';
    abuseReport = await import('../../main/resources/static/js/abuse-report.js');
});

describe('findHelpers', () => {
    it('matches Google org strings', () => {
        expect(abuseReport.findHelpers(GOOGLE_INFO).map(h => h.id)).toEqual(['google']);
    });

    it('matches nothing for a non-Google org', () => {
        expect(abuseReport.findHelpers(OTHER_INFO)).toEqual([]);
    });
});

describe('per-row report button', () => {
    function rowHtml(ip) {
        return `
        <table><tbody>
          <tr data-timestamp="2026-08-29T08:47:02Z" data-uri="/i.php" data-result="Miss" data-status="200" data-country="United States">
            <td class="ip-cell" data-ip="${ip}">${ip}</td>
          </tr>
        </tbody></table>`;
    }

    it('adds a Report to Google button once ip-info:loaded fires for a Google IP', () => {
        document.body.innerHTML += rowHtml(GOOGLE_INFO.ip);
        abuseReport.initAbuseReportButtons();

        const cell = document.querySelector('.ip-cell');
        cell.insertAdjacentHTML('beforeend', '<div class="ip-info-block"></div>');
        cell.dispatchEvent(new CustomEvent('ip-info:loaded', { bubbles: true, detail: { cell, ip: GOOGLE_INFO.ip, info: GOOGLE_INFO } }));

        const btn = cell.querySelector('button');
        expect(btn).not.toBeNull();
        expect(btn.textContent).toBe('Report to Google');
    });

    it('does not add a button for a non-Google IP', () => {
        document.body.innerHTML += rowHtml(OTHER_INFO.ip);
        abuseReport.initAbuseReportButtons();

        const cell = document.querySelector('.ip-cell');
        cell.insertAdjacentHTML('beforeend', '<div class="ip-info-block"></div>');
        cell.dispatchEvent(new CustomEvent('ip-info:loaded', { bubbles: true, detail: { cell, ip: OTHER_INFO.ip, info: OTHER_INFO } }));

        expect(cell.querySelector('button')).toBeNull();
    });

    it('copies a formatted report and opens the form when clicked', async () => {
        document.body.innerHTML += rowHtml(GOOGLE_INFO.ip);
        abuseReport.initAbuseReportButtons();

        const cell = document.querySelector('.ip-cell');
        cell.insertAdjacentHTML('beforeend', '<div class="ip-info-block"></div>');
        cell.dispatchEvent(new CustomEvent('ip-info:loaded', { bubbles: true, detail: { cell, ip: GOOGLE_INFO.ip, info: GOOGLE_INFO } }));

        const writeText = vi.fn().mockResolvedValue();
        vi.stubGlobal('navigator', { clipboard: { writeText } });
        const openMock = vi.fn();
        vi.stubGlobal('open', openMock);

        cell.querySelector('button').click();
        await flushPromises();

        expect(writeText).toHaveBeenCalledTimes(1);
        const text = writeText.mock.calls[0][0];
        expect(text).toBe('2026-08-29 08:47:02 UTC  34.73.59.67  /i.php\n');

        expect(openMock).toHaveBeenCalledWith(
            'https://support.google.com/code/contact/cloud_platform_report', '_blank', 'noopener'
        );
    });
});

describe('bulk report toolbar', () => {
    function bulkPageHtml() {
        return `
        <input type="checkbox" id="selectAllRows">
        <button id="bulkReportBtn" class="d-none">Report to Google</button>
        <table><tbody>
          <tr data-timestamp="2026-08-29T08:47:02Z" data-uri="/i.php" data-result="Miss" data-status="200" data-country="United States">
            <td><input type="checkbox" class="row-select"></td>
            <td class="ip-cell" data-ip="${GOOGLE_INFO.ip}"></td>
          </tr>
          <tr data-timestamp="2026-08-29T09:00:00Z" data-uri="/wp-login.php" data-result="Miss" data-status="404" data-country="France">
            <td><input type="checkbox" class="row-select"></td>
            <td class="ip-cell" data-ip="${OTHER_INFO.ip}"></td>
          </tr>
        </tbody></table>`;
    }

    beforeEach(() => {
        document.body.innerHTML += bulkPageHtml();
    });

    it('is hidden until a row is checked, then shows a count', () => {
        abuseReport.initBulkAbuseReport();
        const btn = document.querySelector('#bulkReportBtn');
        expect(btn.classList.contains('d-none')).toBe(true);

        document.querySelectorAll('.row-select')[0].click();
        expect(btn.classList.contains('d-none')).toBe(false);
        expect(btn.textContent).toBe('Report 1 selected to Google');
    });

    it('select-all checks every row', () => {
        abuseReport.initBulkAbuseReport();
        document.querySelector('#selectAllRows').click();
        const boxes = Array.from(document.querySelectorAll('.row-select'));
        expect(boxes.every(b => b.checked)).toBe(true);
    });

    it('builds a combined report for selected Google rows and warns about non-Google ones', async () => {
        vi.stubGlobal('fetch', vi.fn((url) => {
            const info = url.includes(encodeURIComponent(GOOGLE_INFO.ip)) ? GOOGLE_INFO : OTHER_INFO;
            return Promise.resolve({ json: () => Promise.resolve(info) });
        }));
        const writeText = vi.fn().mockResolvedValue();
        vi.stubGlobal('navigator', { clipboard: { writeText } });
        vi.stubGlobal('open', vi.fn());
        const alertMock = vi.fn();
        vi.stubGlobal('alert', alertMock);

        abuseReport.initBulkAbuseReport();
        const boxes = document.querySelectorAll('.row-select');
        boxes[0].click();
        boxes[1].click();

        document.querySelector('#bulkReportBtn').click();
        await flushPromises();

        expect(alertMock).toHaveBeenCalledTimes(1);
        expect(writeText).toHaveBeenCalledTimes(1);
        expect(writeText.mock.calls[0][0]).toBe('2026-08-29 08:47:02 UTC  34.73.59.67  /i.php\n');
    });

    it('truncates the report and warns when it would exceed the form limit', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ json: () => Promise.resolve(GOOGLE_INFO) }));
        const writeText = vi.fn().mockResolvedValue();
        vi.stubGlobal('navigator', { clipboard: { writeText } });
        vi.stubGlobal('open', vi.fn());
        const alertMock = vi.fn();
        vi.stubGlobal('alert', alertMock);

        // Replace the two-row table with enough Google rows to blow past MAX_REPORT_CHARS.
        document.querySelector('table').remove();
        const rows = Array.from({ length: 40 }, (_, i) => `
          <tr data-timestamp="2026-08-29T08:47:0${i % 10}Z" data-uri="/some/fairly/long/path/${i}.php" data-result="Miss" data-status="200">
            <td><input type="checkbox" class="row-select" checked></td>
            <td class="ip-cell" data-ip="${GOOGLE_INFO.ip}"></td>
          </tr>`).join('');
        document.body.insertAdjacentHTML('beforeend', `<table><tbody>${rows}</tbody></table>`);

        abuseReport.initBulkAbuseReport();
        document.querySelector('#bulkReportBtn').click();
        await flushPromises();

        expect(alertMock).toHaveBeenCalledWith(expect.stringContaining(`limited to ${abuseReport.MAX_REPORT_CHARS} characters`));
        const text = writeText.mock.calls[0][0];
        expect(text.length).toBeLessThanOrEqual(abuseReport.MAX_REPORT_CHARS);
        expect(text).toContain('more (Google\'s form is limited to');
    });
});

describe('select same IP', () => {
    function threeRowPageHtml() {
        return `
        <button id="bulkReportBtn" class="d-none">Report to Google</button>
        <table><tbody>
          <tr><td><input type="checkbox" class="row-select"></td><td class="ip-cell" data-ip="${GOOGLE_INFO.ip}">${GOOGLE_INFO.ip}</td></tr>
          <tr><td><input type="checkbox" class="row-select"></td><td class="ip-cell" data-ip="${GOOGLE_INFO.ip}">${GOOGLE_INFO.ip}</td></tr>
          <tr><td><input type="checkbox" class="row-select"></td><td class="ip-cell" data-ip="${OTHER_INFO.ip}">${OTHER_INFO.ip}</td></tr>
        </tbody></table>`;
    }

    beforeEach(() => {
        document.body.innerHTML += threeRowPageHtml();
    });

    it('adds a select-same-ip button to every ip-cell', () => {
        abuseReport.initSelectSameIp();
        const buttons = document.querySelectorAll('.ip-cell button');
        expect(buttons).toHaveLength(3);
    });

    it('checks only the rows sharing the clicked IP, without triggering the ip-cell lookup click', () => {
        const lookupClick = vi.fn();
        document.querySelectorAll('.ip-cell').forEach(cell => cell.addEventListener('click', lookupClick));

        abuseReport.initSelectSameIp();
        document.querySelectorAll('.ip-cell button')[0].click();

        const rowChecked = Array.from(document.querySelectorAll('.row-select')).map(cb => cb.checked);
        expect(rowChecked).toEqual([true, true, false]);
        expect(lookupClick).not.toHaveBeenCalled();
    });

    it('refreshes the bulk toolbar via row-select:refresh', () => {
        abuseReport.initBulkAbuseReport();
        abuseReport.initSelectSameIp();

        document.querySelectorAll('.ip-cell button')[0].click();

        const btn = document.querySelector('#bulkReportBtn');
        expect(btn.classList.contains('d-none')).toBe(false);
        expect(btn.textContent).toBe('Report 2 selected to Google');
    });
});
