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

import { loadDisobedientSection, initRobotsRefresh, loadFakeBrowsers, loadBrowserRobots, loadBurstIps } from '../../main/resources/static/js/bot-analysis.js';

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
            <table><tbody id="browserRobotsTable"></tbody></table>
            <table><tbody id="burstIpsTable"></tbody></table>
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

    it('loadBrowserRobots renders UA and count', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            json: () => Promise.resolve([{ name: 'FakeChrome/103', count: 56 }]),
        }));
        loadBrowserRobots();
        await flushPromises();

        const row = document.querySelector('#browserRobotsTable tr');
        expect(fetch.mock.calls[0][0]).toContain('/api/browser-robots?');
        expect(row.textContent).toContain('FakeChrome/103');
        expect(row.textContent).toContain('56');
    });

    it('loadBurstIps renders IP, max per minute and total', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            json: () => Promise.resolve([{ clientIp: '20.203.183.116', maxPerMinute: 815, total: 815 }]),
        }));
        loadBurstIps();
        await flushPromises();

        const row = document.querySelector('#burstIpsTable tr');
        expect(fetch.mock.calls[0][0]).toContain('/api/burst-ips?');
        expect(row.textContent).toContain('20.203.183.116');
        expect(row.textContent).toContain('815');
    });

    it('clicking a burst IP cell fetches and renders IP info', async () => {
        const fetchMock = vi.fn()
            .mockResolvedValueOnce({ json: () => Promise.resolve([{ clientIp: '20.203.183.116', maxPerMinute: 815, total: 815 }]) })
            .mockResolvedValueOnce({ json: () => Promise.resolve({ ip: '20.203.183.116', hostname: 'azure.example.com', org: 'AS8075 Microsoft', city: 'Dublin', country: 'IE' }) });
        vi.stubGlobal('fetch', fetchMock);

        loadBurstIps();
        await flushPromises();

        const cell = document.querySelector('#burstIpsTable .ip-cell');
        expect(cell).not.toBeNull();
        cell.click();
        await flushPromises();

        expect(fetchMock.mock.calls[1][0]).toBe('/api/ip-info/20.203.183.116');
        expect(cell.textContent).toContain('AS8075 Microsoft');
        expect(cell.textContent).toContain('azure.example.com');
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
        loadBurstIps();
        await flushPromises();

        expect(document.getElementById('burstIpsTable').textContent)
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