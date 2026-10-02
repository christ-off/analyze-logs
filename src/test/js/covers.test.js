import { describe, it, expect, vi } from 'vitest';
import { flushPromises } from './test-helpers.js';

globalThis.Chart = vi.fn();

describe('covers page', () => {
    it('requests the cover endpoints', async () => {
        document.head.innerHTML = `
            <meta name="cf-from" content="2026-01-01T00:00:00Z">
            <meta name="cf-to"   content="2026-01-31T00:00:00Z">
            <meta name="cf-to-date" content="2026-01-31">
        `;
        document.body.innerHTML = '';
        const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve([]) });
        vi.stubGlobal('fetch', fetchMock);

        await import('../../main/resources/static/js/pages/covers.js');
        await flushPromises();

        const urls = fetchMock.mock.calls.map(c => c[0]);
        expect(urls.some(u => u.startsWith('/api/covers/user-agents?from='))).toBe(true);
        expect(urls.some(u => u.startsWith('/api/covers/user-agent-table?from='))).toBe(true);
        expect(urls.some(u => u.startsWith('/api/covers/referer-split?from='))).toBe(true);
        expect(urls.some(u => u.startsWith('/api/covers/requests-per-day?from='))).toBe(true);
    });
});
