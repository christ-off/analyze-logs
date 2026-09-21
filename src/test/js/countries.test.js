import { describe, it, expect, vi } from 'vitest';
import { flushPromises } from './test-helpers.js';

globalThis.Chart = vi.fn();

const row = (code, name, humanRequests, mastodon, { hit = 5, error = 0 } = {}) => ({
    code, name, hit, miss: 0, function: 0, error,
    total: hit + error, humanRequests, mastodon, searchBots: 0, humanPercentage: 0,
});

describe('countries page', () => {
    it('flags no-human/no-Mastodon countries with a warning and all-error countries with a stop sign', async () => {
        document.head.innerHTML = `
            <meta name="cf-from" content="2026-01-01T00:00:00Z">
            <meta name="cf-to"   content="2026-01-31T00:00:00Z">
        `;
        document.body.innerHTML = `<table><tbody id="countriesTable"></tbody></table>`;
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            json: () => Promise.resolve([
                row('CN', 'China', 0, 0),
                row('FR', 'France', 3, 0),
                row('DE', 'Germany', 0, 2),
                row('RU', 'Russia', 0, 0, { hit: 0, error: 4 }),
            ]),
        }));

        await import('../../main/resources/static/js/pages/countries.js');
        await flushPromises();

        const flagged = [...document.querySelectorAll('#countriesTable tr')]
            .filter(tr => tr.querySelector('td span[title]'))
            .map(tr => [tr.querySelector('a').textContent, tr.querySelector('td span[title]').textContent]);
        expect(flagged).toEqual([['China', '⚠️'], ['Russia', '🛑']]);
    });
});
