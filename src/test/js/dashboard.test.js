import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest';

// Stub the Chart global used by loadAllCharts
globalThis.Chart = { getChart: vi.fn(() => null) };

vi.mock('../../main/resources/static/js/charts.js', () => ({
    Charts: {
        loadChart:             vi.fn(),
        pie:                   vi.fn(),
        horizontalStackedBar:  vi.fn(),
        horizontalBar:         vi.fn(),
        stackedBarByDay:       vi.fn(),
        toDateParam:           vi.fn(d => (d ? d.substring(0, 10) : '')),
    },
}));

vi.mock('../../main/resources/static/js/utils.js', () => ({
    readMeta:       vi.fn(() => '2026-01-01T00:00:00Z'),
    buildBaseParams: vi.fn(() => 'from=2026-01-01&to=2026-01-31'),
    initToggleBots: vi.fn(),   // no-op: don't call loadAllCharts on module load
}));

import { Charts } from '../../main/resources/static/js/charts.js';
import { loadAllCharts, initRefresh } from '../../main/resources/static/js/dashboard.js';

// ── helpers ───────────────────────────────────────────────────────────────────

const REFRESH_HTML = `
    <form id="refreshForm">
        <input type="hidden" name="_csrf" value="tok123">
    </form>
    <button type="button" id="refreshBtn">Refresh from S3</button>
    <div id="refreshProgress" class="d-none">
        <div><div id="refreshBar"
                  class="progress-bar progress-bar-striped progress-bar-animated"
                  style="width:0%" aria-valuenow="0"></div></div>
        <div id="refreshStatus"></div>
    </div>
`;

/** Flush pending microtasks (promise chains). */
async function flushPromises() {
    for (let i = 0; i < 10; i++) await Promise.resolve();
}

function bar()    { return document.getElementById('refreshBar'); }
function status() { return document.getElementById('refreshStatus'); }
function btn()    { return document.getElementById('refreshBtn'); }
function progress() { return document.getElementById('refreshProgress'); }

// ── loadAllCharts ─────────────────────────────────────────────────────────────

describe('loadAllCharts', () => {
    beforeEach(() => vi.clearAllMocks());

    it('calls Charts.loadChart exactly 8 times', () => {
        loadAllCharts();
        expect(Charts.loadChart).toHaveBeenCalledTimes(8);
    });

    it('destroys existing charts before reloading', () => {
        const destroyFn = vi.fn();
        globalThis.Chart.getChart = vi.fn(() => ({ destroy: destroyFn }));
        loadAllCharts();
        expect(destroyFn).toHaveBeenCalledTimes(8);
        globalThis.Chart.getChart = vi.fn(() => null);
    });

    it('ua-names URL builder encodes the UA name', () => {
        loadAllCharts();
        // loadChart call index 2 = ua-names-split
        const [, renderFn] = Charts.loadChart.mock.calls[2];
        renderFn([]);
        const [, , urlBuilder] = Charts.horizontalStackedBar.mock.calls[0];
        expect(urlBuilder({ name: 'Chrome / Windows' }))
            .toMatch(/\/ua-detail\?ua=Chrome/);
    });

    it('countries URL builder encodes the country code', () => {
        loadAllCharts();
        // loadChart call index 3 = countries
        const [, renderFn] = Charts.loadChart.mock.calls[3];
        renderFn([]);
        const [, , urlBuilder] = Charts.horizontalStackedBar.mock.calls[0];
        expect(urlBuilder({ code: 'FR' }))
            .toMatch(/\/country-detail\?country=FR/);
    });

    it('top-urls URL builder encodes the URL path', () => {
        loadAllCharts();
        // loadChart call index 4 = top-urls-split
        const [, renderFn] = Charts.loadChart.mock.calls[4];
        renderFn([]);
        const [, , urlBuilder] = Charts.horizontalStackedBar.mock.calls[0];
        expect(urlBuilder({ name: '/my page' }))
            .toMatch(/\/url-detail\?url=%2Fmy%20page/);
    });
});

// ── initRefresh ───────────────────────────────────────────────────────────────

describe('initRefresh', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        vi.useFakeTimers();
        document.body.innerHTML = REFRESH_HTML;
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it('returns early without throwing when refreshBtn is absent', () => {
        document.body.innerHTML = '';
        expect(() => initRefresh()).not.toThrow();
    });

    it('sends POST /refresh with CSRF header on click', async () => {
        const fetchMock = vi.fn().mockResolvedValue({ status: 202 });
        vi.stubGlobal('fetch', fetchMock);

        initRefresh();
        btn().click();
        await flushPromises();

        expect(fetchMock).toHaveBeenCalledWith('/refresh', {
            method: 'POST',
            headers: { 'X-CSRF-TOKEN': 'tok123' },
        });
    });

    it('omits CSRF header when token input is absent', async () => {
        document.querySelector('#refreshForm input[name="_csrf"]').remove();
        const fetchMock = vi.fn().mockResolvedValue({ status: 202 });
        vi.stubGlobal('fetch', fetchMock);

        initRefresh();
        btn().click();
        await flushPromises();

        const [, opts] = fetchMock.mock.calls[0];
        expect(Object.keys(opts.headers)).toHaveLength(0);
    });

    it('shows progress bar on 202 response', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ status: 202 }));
        initRefresh();
        btn().click();
        await flushPromises();

        expect(progress().classList.contains('d-none')).toBe(false);
        expect(btn().disabled).toBe(true);
    });

    it('shows progress bar on 409 (already running)', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ status: 409 }));
        initRefresh();
        btn().click();
        await flushPromises();

        expect(progress().classList.contains('d-none')).toBe(false);
    });

    it('shows error message on unexpected HTTP status', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ status: 500 }));
        initRefresh();
        btn().click();
        await flushPromises();

        expect(status().textContent).toBe('Start failed (HTTP 500)');
    });

    it('shows network error when POST fetch rejects', async () => {
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
        initRefresh();
        btn().click();
        await flushPromises();

        expect(status().textContent).toBe('Network error');
    });

    // ── poll behaviour ────────────────────────────────────────────────────────

    async function startAndPoll(pollResponse) {
        const fetchMock = vi.fn()
            .mockResolvedValueOnce({ status: 202 })                                   // POST
            .mockResolvedValue({ json: () => Promise.resolve(pollResponse) });        // GET poll
        vi.stubGlobal('fetch', fetchMock);

        initRefresh();
        btn().click();
        await flushPromises();          // POST resolves, setInterval started

        vi.advanceTimersByTime(500);    // trigger first poll tick
        await flushPromises();          // GET + promise chain resolves
    }

    it('poll shows percentage when total > 0', async () => {
        await startAndPoll({ total: 10, processed: 4, done: false });
        expect(status().textContent).toBe('4 / 10 files…');
        expect(bar().style.width).toBe('40%');
    });

    it('poll shows listing message when total is 0', async () => {
        await startAndPoll({ total: 0, processed: 0, done: false });
        expect(status().textContent).toBe('Listing S3 keys…');
    });

    it('poll on success sets bar to green and shows summary', async () => {
        await startAndPoll({ total: 5, processed: 5, fetched: 4, skipped: 1, failed: 0, done: true, error: null });
        expect(bar().classList.contains('bg-success')).toBe(true);
        expect(status().textContent).toContain('Done — fetched: 4, skipped: 1, failed: 0');
    });

    it('poll on success calls loadAllCharts', async () => {
        vi.clearAllMocks();
        await startAndPoll({ total: 1, processed: 1, fetched: 1, skipped: 0, failed: 0, done: true, error: null });
        expect(Charts.loadChart).toHaveBeenCalled();
    });

    it('poll on error sets bar to red and shows error message', async () => {
        await startAndPoll({ total: 5, processed: 2, done: true, error: 'S3 timeout' });
        expect(bar().classList.contains('bg-danger')).toBe(true);
        expect(status().textContent).toBe('Error: S3 timeout');
    });

    it('poll hides progress after timeout on success', async () => {
        await startAndPoll({ total: 1, processed: 1, fetched: 1, skipped: 0, failed: 0, done: true, error: null });
        vi.advanceTimersByTime(5000);
        await flushPromises();
        expect(progress().classList.contains('d-none')).toBe(true);
        expect(btn().disabled).toBe(false);
    });

    it('poll hides progress after timeout on error', async () => {
        await startAndPoll({ total: 1, processed: 0, done: true, error: 'oops' });
        vi.advanceTimersByTime(5000);
        await flushPromises();
        expect(progress().classList.contains('d-none')).toBe(true);
    });

    it('poll network error shows server unreachable message', async () => {
        const fetchMock = vi.fn()
            .mockResolvedValueOnce({ status: 202 })
            .mockRejectedValue(new Error('net::ERR_CONNECTION_REFUSED'));
        vi.stubGlobal('fetch', fetchMock);

        initRefresh();
        btn().click();
        await flushPromises();

        vi.advanceTimersByTime(500);
        await flushPromises();

        expect(status().textContent).toBe('Could not reach server…');
    });
});
