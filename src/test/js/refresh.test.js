import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest';

import { initRefresh } from '../../main/resources/static/js/refresh.js';
import { flushPromises } from './test-helpers.js';

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

function bar()    { return document.getElementById('refreshBar'); }
function status() { return document.getElementById('refreshStatus'); }
function btn()    { return document.getElementById('refreshBtn'); }
function progress() { return document.getElementById('refreshProgress'); }

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
        expect(() => initRefresh(vi.fn())).not.toThrow();
    });

    it('sends POST /refresh with CSRF header on click', async () => {
        const fetchMock = vi.fn().mockResolvedValue({ status: 202 });
        vi.stubGlobal('fetch', fetchMock);

        initRefresh(vi.fn());
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

        initRefresh(vi.fn());
        btn().click();
        await flushPromises();

        const [, opts] = fetchMock.mock.calls[0];
        expect(Object.keys(opts.headers)).toHaveLength(0);
    });

    it('shows progress bar on 202 response', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ status: 202 }));
        initRefresh(vi.fn());
        btn().click();
        await flushPromises();

        expect(progress().classList.contains('d-none')).toBe(false);
        expect(btn().disabled).toBe(true);
    });

    it('shows progress bar on 409 (already running)', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ status: 409 }));
        initRefresh(vi.fn());
        btn().click();
        await flushPromises();

        expect(progress().classList.contains('d-none')).toBe(false);
    });

    it('shows error message on unexpected HTTP status', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ status: 500 }));
        initRefresh(vi.fn());
        btn().click();
        await flushPromises();

        expect(status().textContent).toBe('Start failed (HTTP 500)');
    });

    it('shows network error when POST fetch rejects', async () => {
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
        initRefresh(vi.fn());
        btn().click();
        await flushPromises();

        expect(status().textContent).toBe('Network error');
    });

    // ── poll behaviour ────────────────────────────────────────────────────────

    async function startAndPoll(pollResponse, onSuccess = vi.fn()) {
        const fetchMock = vi.fn()
            .mockResolvedValueOnce({ status: 202 })                                   // POST
            .mockResolvedValue({ json: () => Promise.resolve(pollResponse) });        // GET poll
        vi.stubGlobal('fetch', fetchMock);

        initRefresh(onSuccess);
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

    it('poll on success calls the onSuccess callback', async () => {
        const onSuccess = vi.fn();
        await startAndPoll({ total: 1, processed: 1, fetched: 1, skipped: 0, failed: 0, done: true, error: null }, onSuccess);
        expect(onSuccess).toHaveBeenCalled();
    });

    it('poll on error sets bar to red and shows error message', async () => {
        await startAndPoll({ total: 5, processed: 2, done: true, error: 'S3 timeout' });
        expect(bar().classList.contains('bg-danger')).toBe(true);
        expect(status().textContent).toBe('Error: S3 timeout');
    });

    it('poll on error does not call the onSuccess callback', async () => {
        const onSuccess = vi.fn();
        await startAndPoll({ total: 5, processed: 2, done: true, error: 'S3 timeout' }, onSuccess);
        expect(onSuccess).not.toHaveBeenCalled();
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

        initRefresh(vi.fn());
        btn().click();
        await flushPromises();

        vi.advanceTimersByTime(500);
        await flushPromises();

        expect(status().textContent).toBe('Could not reach server…');
    });
});
