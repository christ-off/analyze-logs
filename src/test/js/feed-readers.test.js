import { describe, it, expect, vi } from 'vitest';
import { flushPromises } from './test-helpers.js';

globalThis.Chart = vi.fn();

describe('feed readers page', () => {
    it('requests the feed reader endpoints', async () => {
        document.head.innerHTML = `
            <meta name="cf-from" content="2026-01-01T00:00:00Z">
            <meta name="cf-to"   content="2026-01-31T00:00:00Z">
            <meta name="cf-to-date" content="2026-01-31">
        `;
        document.body.innerHTML = '';
        const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve([]) });
        vi.stubGlobal('fetch', fetchMock);

        await import('../../main/resources/static/js/pages/feed-readers.js');
        await flushPromises();

        const urls = fetchMock.mock.calls.map(c => c[0]);
        expect(urls.some(u => u.startsWith('/api/feed-readers/user-agents?from='))).toBe(true);
        expect(urls.some(u => u.startsWith('/api/feed-readers/subscribers-per-day?from='))).toBe(true);
    });

    // Shared loadUaChart behavior: every bar of the user agents chart is a link to /ua-detail.
    it('renders the user agents chart with clickable bars', async () => {
        document.head.innerHTML = `
            <meta name="cf-from" content="2026-01-01T00:00:00Z">
            <meta name="cf-to"   content="2026-01-31T00:00:00Z">
            <meta name="cf-to-date" content="2026-01-31">
        `;
        document.body.innerHTML = '<canvas id="chartFeedReaders"></canvas>';
        const ChartMock = globalThis.Chart;
        ChartMock.getChart = vi.fn(() => null);
        ChartMock.mockClear();
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            json: () => Promise.resolve([{ name: 'Mastodon/4.0', hit: 3, miss: 1, function: 0, error: 0 }]),
        });
        vi.stubGlobal('fetch', fetchMock);

        vi.resetModules();
        await import('../../main/resources/static/js/pages/feed-readers.js');
        await flushPromises();

        const config = ChartMock.mock.calls.map(c => c[1]).find(cfg => cfg?.data?.labels?.includes('Mastodon/4.0'));
        expect(config).toBeDefined();
        expect(config.options.onClick).toBeTypeOf('function');
        expect(config.options.onHover).toBeTypeOf('function');
    });
});
