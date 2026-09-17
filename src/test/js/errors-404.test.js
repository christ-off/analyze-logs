import { beforeEach, describe, it, expect, vi } from 'vitest';

vi.mock('../../main/resources/static/js/utils.js', async (importOriginal) => ({
    ...(await importOriginal()),
    buildBaseParams: vi.fn(() => 'from=2026-01-01&to=2026-01-31'),
    escapeHtml:      vi.fn((s) => s),
}));

import { loadUris } from '../../main/resources/static/js/pages/errors-404.js';
import { flushPromises } from './test-helpers.js';

const PAGE_HTML = `
    <span id="errors404Count"></span>
    <table><tbody id="errors404Table"><tr><td colspan="2">Loading...</td></tr></tbody></table>
`;

const SAMPLE_URI = { name: '/.env', count: 44 };

describe('errors-404 uris table', () => {
    beforeEach(() => {
        document.body.innerHTML = PAGE_HTML;
        vi.clearAllMocks();
    });

    it('calls /api/errors-404/uris with the base params', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ json: () => Promise.resolve([]) }));
        loadUris();
        await flushPromises();

        expect(fetch.mock.calls[0][0]).toContain('/api/errors-404/uris?');
    });

    it('renders one row per uri: uri and count only', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ json: () => Promise.resolve([SAMPLE_URI]) }));
        loadUris();
        await flushPromises();

        const row = document.querySelector('#errors404Table tr');
        expect(row.textContent).toContain('/.env');
        expect(row.textContent).toContain('44');
        expect(row.querySelectorAll('td')).toHaveLength(2);
    });

    it('updates the row-count label', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ json: () => Promise.resolve([SAMPLE_URI]) }));
        loadUris();
        await flushPromises();

        expect(document.getElementById('errors404Count').textContent).toContain('1');
    });

    it('shows empty state when array is empty', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ json: () => Promise.resolve([]) }));
        loadUris();
        await flushPromises();

        expect(document.getElementById('errors404Table').textContent)
            .toContain('No 404s or errors found');
    });

    it('shows error state on fetch failure', async () => {
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network')));
        loadUris();
        await flushPromises();

        expect(document.getElementById('errors404Table').textContent)
            .toContain('Failed to load');
    });
});
