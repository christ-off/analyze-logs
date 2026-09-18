import { describe, it, expect, vi } from 'vitest';
import { flushPromises } from './test-helpers.js';

// charts.js (imported transitively via utils.js) references Chart via globalThis
globalThis.Chart = vi.fn();

// Rendering behaviour itself is covered by the loadUriCountTable tests in utils.test.js.
describe('zip-requests page', () => {
    it('loads the zip uris into the page table', async () => {
        // The page module loads its table as soon as it is imported, so the meta tags and
        // the tbody it renders into have to be in place before the import.
        document.head.innerHTML = `
            <meta name="cf-from" content="2026-01-01T00:00:00Z">
            <meta name="cf-to"   content="2026-01-31T00:00:00Z">
        `;
        document.body.innerHTML = `
            <span id="zipRequestsCount"></span>
            <table><tbody id="zipRequestsTable"><tr><td colspan="2">Loading...</td></tr></tbody></table>
        `;
        const fetchMock = vi.fn().mockResolvedValue({
            json: () => Promise.resolve([{ name: '/backup.zip', count: 44 }]),
        });
        vi.stubGlobal('fetch', fetchMock);

        await import('../../main/resources/static/js/pages/zip-requests.js');
        await flushPromises();

        expect(fetchMock.mock.calls[0][0]).toContain('/api/zip-requests/uris?from=');
        expect(document.querySelector('#zipRequestsTable tr').textContent).toContain('/backup.zip');
        expect(document.getElementById('zipRequestsCount').textContent).toContain('1');
    });
});
