import { beforeEach, describe, it, expect, vi } from 'vitest';

vi.mock('../../main/resources/static/js/utils.js', () => ({
    buildBaseParams:  vi.fn(() => 'from=2026-01-01&to=2026-01-31'),
    escapeHtml:       vi.fn((s) => s),
    formatTimestamp:  vi.fn((iso) => iso),
    detailUrl:        vi.fn((path, params) => `${path}?ua=${params.ua}`),
}));

import { loadSocialReferrals } from '../../main/resources/static/js/social-referrals.js';
import { flushPromises } from './test-helpers.js';

const HTML = `
    <div id="socialReferralsSections">
        <table><tbody data-network="Facebook"><tr><td colspan="4">Loading...</td></tr></tbody></table>
        <table><tbody data-network="Mastodon"><tr><td colspan="4">Loading...</td></tr></tbody></table>
        <table><tbody data-network="WhatsApp"><tr><td colspan="4">Loading...</td></tr></tbody></table>
    </div>
`;

const section = (network) => document.querySelector(`tbody[data-network="${network}"]`);

const SAMPLE_RESPONSE = {
    Facebook: [
        { timestamp: '2026-08-24T11:06:46Z', userAgent: 'facebookexternalhit/1.1',
            uaName: 'Facebook', uriStem: '/article/', country: 'United States' },
    ],
    Mastodon: [
        { timestamp: '2026-08-24T12:00:00Z', userAgent: 'http.rb/5.1.1 (Mastodon/4.2.17)',
            uaName: 'Mastodon', uriStem: '/toot/', country: '-' },
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

        const fb = section('Facebook');
        expect(fb.textContent).toContain('/article/');
        expect(fb.textContent).toContain('United States');
        const uaLink = fb.querySelector('a');
        expect(uaLink.getAttribute('href')).toBe('/ua-detail?ua=Facebook');
        expect(uaLink.textContent).toContain('facebookexternalhit/1.1');
        expect(fb.querySelectorAll('td')).toHaveLength(4);

        const mastodon = section('Mastodon');
        expect(mastodon.textContent).toContain('/toot/');
    });

    it('shows an empty state for networks with no hits', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            json: () => Promise.resolve(SAMPLE_RESPONSE),
        }));
        loadSocialReferrals();
        await flushPromises();

        expect(section('WhatsApp').textContent).toContain('No hits from this network');
    });

    it('shows a failure message in every section when the fetch rejects', async () => {
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network down')));
        loadSocialReferrals();
        await flushPromises();

        expect(section('Facebook').textContent).toContain('Failed to load data.');
        expect(section('WhatsApp').textContent).toContain('Failed to load data.');
    });
});
