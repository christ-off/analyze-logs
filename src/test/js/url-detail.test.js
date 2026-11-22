import { describe, it, expect, vi } from 'vitest';
import { flushPromises } from './test-helpers.js';

globalThis.Chart = vi.fn();

describe('url detail page', () => {
    // Shared loadUaChart behavior: every bar of the "Top User Agents" chart is a link to /ua-detail.
    it('requests the url detail endpoints and renders the user agents chart with clickable bars', async () => {
        document.head.innerHTML = `
            <meta name="cf-from" content="2026-01-01T00:00:00Z">
            <meta name="cf-to"   content="2026-01-31T00:00:00Z">
            <meta name="cf-url"  content="/feed.xml">
        `;
        document.body.innerHTML = '<canvas id="chartUserAgents"></canvas><canvas id="chartCountries"></canvas><table><tbody id="tbodyUrls"></tbody></table>';
        const ChartMock = globalThis.Chart;
        ChartMock.getChart = vi.fn(() => null);
        const fetchMock = vi.fn(url => Promise.resolve({
            ok: true,
            json: () => Promise.resolve(url.startsWith('/api/url-detail/countries?')
                ? [{ code: 'FR', name: 'France', hit: 2, miss: 1, function: 0, error: 0 }]
                : [{ name: 'Mastodon/4.0', hit: 3, miss: 1, function: 0, error: 0 }]),
        }));
        vi.stubGlobal('fetch', fetchMock);

        await import('../../main/resources/static/js/url-detail.js');
        await flushPromises();

        const urls = fetchMock.mock.calls.map(c => c[0]);
        expect(urls.some(u => u.startsWith('/api/url-detail/user-agents?url='))).toBe(true);
        expect(urls.some(u => u.startsWith('/api/url-detail/countries?url='))).toBe(true);
        expect(urls.some(u => u.startsWith('/api/url-detail/requests-per-day?url='))).toBe(true);

        for (const label of ['Mastodon/4.0', 'France']) {
            const config = ChartMock.mock.calls.map(c => c[1]).find(cfg => cfg?.data?.labels?.includes(label));
            expect(config).toBeDefined();
            expect(config.options.onClick).toBeTypeOf('function');
            expect(config.options.onHover).toBeTypeOf('function');
        }
    });
});
