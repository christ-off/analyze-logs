import { beforeEach, describe, it, expect, vi } from 'vitest';

vi.mock('../../main/resources/static/js/utils.js', () => ({
    buildBaseParams: vi.fn(() => 'from=2026-01-01&to=2026-01-31'),
    escapeHtml:      vi.fn((s) => s),
    stackedBar:      vi.fn(() => '<div class="bar"></div>'),
    detailUrl:       vi.fn((path, params) => `${path}?ua=${params.ua}`),
}));

import { loadSocialReferrals } from '../../main/resources/static/js/social-referrals.js';

async function flushPromises() {
    for (let i = 0; i < 10; i++) await Promise.resolve();
}

const HTML = `
    <table><tbody id="sr-Facebook"><tr><td colspan="5">Loading...</td></tr></tbody></table>
    <table><tbody id="sr-Discord"><tr><td colspan="5">Loading...</td></tr></tbody></table>
    <table><tbody id="sr-TwitterX"><tr><td colspan="5">Loading...</td></tr></tbody></table>
    <table><tbody id="sr-WhatsApp"><tr><td colspan="5">Loading...</td></tr></tbody></table>
`;

const SAMPLE_RESPONSE = {
    Facebook: [
        { network: 'Facebook', timestamp: '2026-08-24T11:06:46Z', userAgent: 'facebookexternalhit/1.1',
            uaName: 'Facebook', uriStem: '/article/', country: 'United States', hit: 1, miss: 0, function: 0, error: 0 },
    ],
    Discord: [],
    'Twitter/X': [
        { network: 'Twitter/X', timestamp: '2026-08-24T12:00:00Z', userAgent: 'Twitterbot/1.0',
            uaName: 'Unknown', uriStem: '/photo/', country: '-', hit: 0, miss: 1, function: 0, error: 0 },
    ],
    WhatsApp: [],
};

describe('loadSocialReferrals', () => {
    beforeEach(() => {
        document.body.innerHTML = HTML;
        vi.clearAllMocks();
    });

    it('renders one row per request under its network container', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            json: () => Promise.resolve(SAMPLE_RESPONSE),
        }));
        loadSocialReferrals();
        await flushPromises();

        const fb = document.getElementById('sr-Facebook');
        expect(fb.textContent).toContain('/article/');
        expect(fb.textContent).toContain('United States');
        const uaLink = fb.querySelector('a');
        expect(uaLink.getAttribute('href')).toBe('/ua-detail?ua=Facebook');
        expect(uaLink.textContent).toContain('facebookexternalhit/1.1');
        expect(fb.querySelector('.bar')).not.toBeNull();

        const twitter = document.getElementById('sr-TwitterX');
        expect(twitter.textContent).toContain('/photo/');
    });

    it('shows an empty state for networks with no hits', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            json: () => Promise.resolve(SAMPLE_RESPONSE),
        }));
        loadSocialReferrals();
        await flushPromises();

        expect(document.getElementById('sr-Discord').textContent).toContain('No hits from this network');
    });

    it('shows a failure message in every section when the fetch rejects', async () => {
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network down')));
        loadSocialReferrals();
        await flushPromises();

        expect(document.getElementById('sr-Facebook').textContent).toContain('Failed to load data.');
        expect(document.getElementById('sr-WhatsApp').textContent).toContain('Failed to load data.');
    });
});
