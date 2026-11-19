import { beforeEach, describe, it, expect, vi } from 'vitest';
import { flushPromises } from './test-helpers.js';

globalThis.Chart = vi.fn();

function setupPage() {
    document.head.innerHTML = `
        <meta name="cf-from"    content="2026-01-01T00:00:00Z">
        <meta name="cf-to"      content="2026-01-31T00:00:00Z">
        <meta name="cf-to-date" content="2026-01-31">
    `;
    document.body.innerHTML = `
        <table><tbody id="blockedFeedReadersTable"></tbody></table>
        <table><tbody id="blockedFediverseTable"></tbody></table>
    `;
}

describe('blocked-hosted-apps page', () => {
    beforeEach(() => {
        // The page module runs its loaders at import time — reset the module registry
        // so each test gets a fresh execution.
        vi.resetModules();
        setupPage();
    });

    it('requests both blocked-app endpoints for the range', async () => {
        const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve([]) });
        vi.stubGlobal('fetch', fetchMock);

        await import('../../main/resources/static/js/pages/blocked-hosted-apps.js');
        await flushPromises();

        const urls = fetchMock.mock.calls.map(c => c[0]);
        expect(urls.some(u => u.startsWith('/api/blocked-feed-readers?from=2026-01-01&to=2026-01-31'))).toBe(true);
        expect(urls.some(u => u.startsWith('/api/blocked-fediverse?from=2026-01-01&to=2026-01-31'))).toBe(true);
    });

    it('links each raw user agent to /ua-requests, not /ua-detail', async () => {
        const rows = [{ userAgent: 'SomeFeedReader/2.0', count: 7 }];
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(rows) }));

        await import('../../main/resources/static/js/pages/blocked-hosted-apps.js');
        await flushPromises();

        for (const id of ['blockedFeedReadersTable', 'blockedFediverseTable']) {
            const link = document.getElementById(id).querySelector('a');
            expect(decodeURIComponent(link.getAttribute('href')))
                .toBe('/ua-requests?ua=SomeFeedReader/2.0&from=2026-01-01&to=2026-01-31');
            expect(link.textContent).toBe('SomeFeedReader/2.0'); // full string, not truncated
        }
    });
});
