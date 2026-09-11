import { beforeEach, describe, it, expect, vi } from 'vitest';

vi.mock('../../main/resources/static/js/utils.js', () => ({
    buildBaseParams: vi.fn(() => 'from=2026-01-01&to=2026-01-31'),
    escapeHtml:      vi.fn((s) => s),
    resultTotal:     (row) => row.hit + row.miss + (row['function'] ?? 0) + row.error,
    stackedBar:      vi.fn(() => '<div class="bar"></div>'),
    uaRequestsUrl:   vi.fn((ua) => `/ua-requests?ua=${ua}`),
}));

import { loadIdentityShifts } from '../../main/resources/static/js/bene-tleilax.js';

async function flushPromises() {
    for (let i = 0; i < 10; i++) await Promise.resolve();
}

const HTML = `<div id="identityShiftsList">Loading...</div>`;

const SAMPLE_ENTRY = {
    ip: '185.213.175.37',
    firstSeen: '2026-07-06T20:28:06Z',
    lastSeen: '2026-08-24T11:06:46Z',
    userAgents: [
        { name: 'GoogleOther/1.0', count: 106 },
        { name: 'Baiduspider/2.0', count: 86 },
    ],
    urls: [
        { name: '/hero.webp', hit: 0, miss: 0, function: 12, error: 0, userAgents: ['Baiduspider/2.0'] },
    ],
};

describe('loadIdentityShifts', () => {
    beforeEach(() => {
        document.body.innerHTML = HTML;
        vi.clearAllMocks();
    });

    it('renders one entry per identity-shifting IP', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            json: () => Promise.resolve([SAMPLE_ENTRY]),
        }));
        loadIdentityShifts();
        await flushPromises();

        const container = document.getElementById('identityShiftsList');
        expect(container.textContent).toContain('185.213.175.37');
        expect(container.textContent).toContain('2 identities');
        expect(container.textContent).toContain('/hero.webp');
        expect(container.textContent).toContain('Baiduspider/2.0');

        // the user agent sits in the same row as the URL and the result-type bar
        const row = container.querySelector('tbody tr');
        expect(row.textContent).toContain('/hero.webp');
        const uaLink = row.querySelector('a.badge');
        expect(uaLink.getAttribute('href')).toBe('/ua-requests?ua=Baiduspider/2.0');
        expect(row.querySelector('.bar')).not.toBeNull();
    });

    it('shows empty state when array is empty', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            json: () => Promise.resolve([]),
        }));
        loadIdentityShifts();
        await flushPromises();

        expect(document.getElementById('identityShiftsList').textContent)
            .toContain('No face dancers found');
    });

    it('shows a failure message when the fetch rejects', async () => {
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network down')));
        loadIdentityShifts();
        await flushPromises();

        expect(document.getElementById('identityShiftsList').textContent)
            .toContain('Failed to load data.');
    });
});
