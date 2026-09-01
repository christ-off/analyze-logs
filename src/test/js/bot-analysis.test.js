import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest';

globalThis.Chart = { getChart: vi.fn(() => null) };

vi.mock('../../main/resources/static/js/charts.js', () => ({
    Charts: {
        COLORS: { green: '#4CAF50', blue: '#2196F3', orange: '#FF9800', red: '#f44336' },
        loadChart: vi.fn(),
    },
}));

vi.mock('../../main/resources/static/js/utils.js', () => ({
    buildBaseParams: vi.fn(() => 'from=2026-01-01&to=2026-01-31'),
    escapeHtml:      vi.fn((s) => s),
    initToggleBots:  vi.fn(),
    readMeta:        vi.fn(() => '2026-01-01'),
    resultTotal:     (row) => row.hit + row.miss + (row['function'] ?? 0) + row.error,
    stackedBar:      vi.fn(() => ''),
    uaRequestsUrl:   vi.fn((ua) => `/ua-requests?ua=${ua}`),
}));

import { loadDisobedientSection, initRobotsRefresh, loadFakeBrowsers, loadBrowserConfigFetches, loadBrowserLlmsTxtFetches } from '../../main/resources/static/js/bot-analysis.js';

async function flushPromises() {
    for (let i = 0; i < 10; i++) await Promise.resolve();
}

const BOTS_HTML = `
    <table>
        <tbody id="disobedientBotsTable"><tr><td colspan="3">Loading...</td></tr></tbody>
    </table>
    <span id="robotsRefreshedAt"></span>
    <button id="refreshRobotsBtn">Refresh Robots</button>
`;

const SAMPLE_BOT = { userAgent: 'BadBot/1.0', count: 5, hit: 3, miss: 1, error: 1, function: 0 };

describe('loadDisobedientSection', () => {
    beforeEach(() => {
        document.body.innerHTML = BOTS_HTML;
        vi.clearAllMocks();
    });

    it('renders rows for each bot', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            json: () => Promise.resolve([SAMPLE_BOT]),
        }));
        loadDisobedientSection();
        await flushPromises();

        const rows = document.querySelectorAll('#disobedientBotsTable tr');
        expect(rows).toHaveLength(1);
        expect(rows[0].textContent).toContain('BadBot/1.0');
        expect(rows[0].textContent).toContain('5');
    });

    it('shows empty state when array is empty', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            json: () => Promise.resolve([]),
        }));
        loadDisobedientSection();
        await flushPromises();

        expect(document.getElementById('disobedientBotsTable').textContent)
            .toContain('No disobedient bots found');
    });

    it('shows error state on fetch failure', async () => {
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network')));
        loadDisobedientSection();
        await flushPromises();

        expect(document.getElementById('disobedientBotsTable').textContent)
            .toContain('Failed to load');
    });
});

describe('bot signal tables', () => {
    beforeEach(() => {
        document.body.innerHTML = `
            <table><tbody id="fakeBrowsersTable"></tbody></table>
            <table><tbody id="browserConfigTable"></tbody></table>
            <table><tbody id="browserLlmsTxtTable"></tbody></table>
        `;
        vi.clearAllMocks();
    });

    it('loadFakeBrowsers renders UA, count, hours and days', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            json: () => Promise.resolve([{ userAgent: 'FakeChrome/70', count: 17983, activeHours: 24, days: 67 }]),
        }));
        loadFakeBrowsers();
        await flushPromises();

        const row = document.querySelector('#fakeBrowsersTable tr');
        expect(fetch.mock.calls[0][0]).toContain('/api/fake-browsers?');
        expect(row.textContent).toContain('FakeChrome/70');
        expect(row.textContent).toContain('24 / 24');
        expect(row.textContent).toContain('67');
    });

    it('loadBrowserConfigFetches renders UA and count', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            json: () => Promise.resolve([{ name: 'FakeChrome/103', hit: 50, miss: 4, function: 1, error: 1 }]),
        }));
        loadBrowserConfigFetches();
        await flushPromises();

        const row = document.querySelector('#browserConfigTable tr');
        expect(fetch.mock.calls[0][0]).toContain('/api/browser-config?');
        expect(row.textContent).toContain('FakeChrome/103');
        expect(row.textContent).toContain('56');
    });

    it('loadBrowserLlmsTxtFetches renders UA and count', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            json: () => Promise.resolve([{ name: 'FakeChrome/103', hit: 10, miss: 2, function: 0, error: 0 }]),
        }));
        loadBrowserLlmsTxtFetches();
        await flushPromises();

        const row = document.querySelector('#browserLlmsTxtTable tr');
        expect(fetch.mock.calls[0][0]).toContain('/api/browser-llms-txt?');
        expect(row.textContent).toContain('FakeChrome/103');
        expect(row.textContent).toContain('12');
    });

    it('shows empty state when no rows', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            json: () => Promise.resolve([]),
        }));
        loadFakeBrowsers();
        await flushPromises();

        expect(document.getElementById('fakeBrowsersTable').textContent)
            .toContain('No round-the-clock browser UAs');
    });

    it('shows error state on fetch failure', async () => {
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network')));
        loadFakeBrowsers();
        await flushPromises();

        expect(document.getElementById('fakeBrowsersTable').textContent)
            .toContain('Failed to load');
    });
});

describe('initRobotsRefresh', () => {
    beforeEach(() => {
        document.body.innerHTML = BOTS_HTML;
        vi.clearAllMocks();
    });

    it('returns without throwing when button is absent', () => {
        document.body.innerHTML = '';
        expect(() => initRobotsRefresh()).not.toThrow();
    });

    it('calls /api/robots-refresh on click', async () => {
        const fetchMock = vi.fn()
            .mockResolvedValueOnce({ text: () => Promise.resolve('OK') })  // robots-refresh
            .mockResolvedValue({ json: () => Promise.resolve([]) });        // reload
        vi.stubGlobal('fetch', fetchMock);

        initRobotsRefresh();
        document.getElementById('refreshRobotsBtn').click();
        await flushPromises();

        expect(fetchMock.mock.calls[0][0]).toBe('/api/robots-refresh');
    });

    it('updates refreshed-at label with response text', async () => {
        vi.stubGlobal('fetch', vi.fn()
            .mockResolvedValueOnce({ text: () => Promise.resolve('OK — refreshed at 2026-05-30') })
            .mockResolvedValue({ json: () => Promise.resolve([]) }));

        initRobotsRefresh();
        document.getElementById('refreshRobotsBtn').click();
        await flushPromises();

        expect(document.getElementById('robotsRefreshedAt').textContent)
            .toContain('OK — refreshed at');
    });
});