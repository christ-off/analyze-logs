import { describe, it, expect, vi } from 'vitest';
import { flushPromises } from './test-helpers.js';

globalThis.Chart = vi.fn();

describe('asn page', () => {
    it('requests the top ASN endpoint with the selected range', async () => {
        document.head.innerHTML = `
            <meta name="cf-from" content="2026-01-01T00:00:00Z">
            <meta name="cf-to"   content="2026-01-31T00:00:00Z">
            <meta name="cf-to-date" content="2026-01-31">
        `;
        document.body.innerHTML = '';
        const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve([]) });
        vi.stubGlobal('fetch', fetchMock);

        await import('../../main/resources/static/js/pages/asn.js');
        await flushPromises();

        const urls = fetchMock.mock.calls.map(c => c[0]);
        expect(urls.some(u => u.startsWith('/api/asns?from='))).toBe(true);
    });
});
