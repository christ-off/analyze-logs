import { describe, it, expect, vi, beforeEach } from 'vitest';
import { flushPromises } from './test-helpers.js';

globalThis.Chart = vi.fn();

function setupPage() {
    document.head.innerHTML = `
        <meta name="cf-from" content="2026-01-01T00:00:00Z">
        <meta name="cf-to"   content="2026-01-31T00:00:00Z">
        <meta name="cf-to-date" content="2026-01-31">
    `;
    document.body.innerHTML = `
        <form id="refreshForm"><input type="hidden" name="_csrf" value="tok"></form>
        <span id="asnNamesStatus"></span>
        <button id="asnNamesBtn"></button>
        <table><tbody id="asnTable"></tbody></table>
    `;
}

describe('asn page', () => {
    beforeEach(() => {
        vi.resetModules();
        setupPage();
    });

    it('requests the top ASN endpoint with the selected range', async () => {
        const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve([]) });
        vi.stubGlobal('fetch', fetchMock);

        await import('../../main/resources/static/js/pages/asn.js');
        await flushPromises();

        const urls = fetchMock.mock.calls.map(c => c[0]);
        expect(urls.some(u => u.startsWith('/api/asns?from='))).toBe(true);
    });

    it('renders one row per ASN with name, category, % human and feed requests', async () => {
        const rows = [{ asn: '24940', name: 'Hetzner', category: 'hosting', hit: 8, miss: 2, function: 0, error: 0,
            total: 10, humanPercentage: 20, feeds: 3 }];
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(rows) }));

        await import('../../main/resources/static/js/pages/asn.js');
        await flushPromises();

        const cells = [...document.querySelectorAll('#asnTable td')].map(td => td.textContent.trim());
        expect(cells.slice(0, 6)).toEqual(['AS24940', 'Hetzner', 'hosting', '20.0%', '3', '10']);
        expect(document.querySelector('#asnTable a').getAttribute('href')).toContain('asn=24940');
    });

    it('downloads the ASN names with the CSRF token and reports the count', async () => {
        const fetchMock = vi.fn().mockImplementation(url => Promise.resolve({
            ok: true,
            json: () => Promise.resolve(url === '/asn/names/refresh' ? { count: 1234 } : []),
        }));
        vi.stubGlobal('fetch', fetchMock);

        await import('../../main/resources/static/js/pages/asn.js');
        await flushPromises();
        document.getElementById('asnNamesBtn').click();
        await flushPromises();

        const call = fetchMock.mock.calls.find(c => c[0] === '/asn/names/refresh');
        expect(call[1].method).toBe('POST');
        expect(call[1].headers['X-CSRF-TOKEN']).toBe('tok');
        expect(document.getElementById('asnNamesStatus').textContent).toContain('1,234');
    });

    it('shows the error when the download fails', async () => {
        const fetchMock = vi.fn().mockImplementation(url => Promise.resolve(
            url === '/asn/names/refresh'
                ? { ok: false, json: () => Promise.resolve({ error: 'Download failed: boom' }) }
                : { ok: true, json: () => Promise.resolve([]) }));
        vi.stubGlobal('fetch', fetchMock);

        await import('../../main/resources/static/js/pages/asn.js');
        await flushPromises();
        document.getElementById('asnNamesBtn').click();
        await flushPromises();

        expect(document.getElementById('asnNamesStatus').textContent).toBe('Download failed: boom');
    });
});
