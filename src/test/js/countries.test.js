import { describe, it, expect, vi } from 'vitest';
import { flushPromises } from './test-helpers.js';

globalThis.Chart = vi.fn();

const row = (code, name, humanRequests, mastodon, { hit = 5, error = 0, feeds = 0, searchBots = 0 } = {}) => ({
    code, name, hit, miss: 0, function: 0, error,
    total: hit + error, humanRequests, mastodon, searchBots, feeds, humanPercentage: 0,
});

describe('countries page', () => {
    it('flags no-human/no-Mastodon/no-feed countries with a warning and all-error countries with a stop sign', async () => {
        document.head.innerHTML = `
            <meta name="cf-from" content="2026-01-01T00:00:00Z">
            <meta name="cf-to"   content="2026-01-31T00:00:00Z">
        `;
        document.body.innerHTML = `
            <input type="checkbox" id="filterWarning" checked>
            <input type="checkbox" id="filterBlocked" checked>
            <input type="checkbox" id="filterPerson" checked>
            <table><tbody id="countriesTable"></tbody></table>
        `;
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            json: () => Promise.resolve([
                row('CN', 'China', 0, 0),
                row('FR', 'France', 3, 0),
                row('DE', 'Germany', 0, 2),
                row('RU', 'Russia', 0, 0, { hit: 0, error: 4 }),
                row('JP', 'Japan', 0, 0, { feeds: 1 }),
                row('US', 'United States', 0, 0, { searchBots: 2 }),
            ]),
        }));

        await import('../../main/resources/static/js/pages/countries.js');
        await flushPromises();

        const rowsByCountry = name => [...document.querySelectorAll('#countriesTable tr')]
            .find(tr => tr.querySelector('a').textContent === name);
        const blockSign = tr => tr.querySelector('td span[title="All requests are errors"], td span[title^="No human"]');

        const flagged = [...document.querySelectorAll('#countriesTable tr')]
            .filter(blockSign)
            .map(tr => [tr.querySelector('a').textContent, blockSign(tr).textContent]);
        expect(flagged).toEqual([['China', '⚠️'], ['Russia', '🛑']]);

        expect(rowsByCountry('France').querySelector('td span[title="Has human requests"]').textContent).toBe('🧑');
        expect(rowsByCountry('China').querySelector('td span[title="Has human requests"]')).toBeNull();
        expect(rowsByCountry('Germany').querySelector('td span[title="Has Mastodon requests"]').textContent).toBe('🧑');
        expect(rowsByCountry('China').querySelector('td span[title="Has Mastodon requests"]')).toBeNull();

        const countryNames = () => [...document.querySelectorAll('#countriesTable tr a')].map(a => a.textContent);

        document.getElementById('filterWarning').checked = false;
        document.getElementById('filterWarning').dispatchEvent(new Event('change'));
        expect(countryNames()).not.toContain('China');
        expect(countryNames()).toContain('Russia');

        document.getElementById('filterBlocked').checked = false;
        document.getElementById('filterBlocked').dispatchEvent(new Event('change'));
        expect(countryNames()).not.toContain('Russia');
        expect(countryNames()).toEqual(expect.arrayContaining(['France', 'Germany', 'Japan', 'United States']));

        document.getElementById('filterPerson').checked = false;
        document.getElementById('filterPerson').dispatchEvent(new Event('change'));
        expect(countryNames()).not.toContain('France');
        expect(countryNames()).not.toContain('Germany');
        expect(countryNames()).toEqual(expect.arrayContaining(['Japan', 'United States']));
    });
});
