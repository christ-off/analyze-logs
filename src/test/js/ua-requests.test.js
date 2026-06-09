import { beforeEach, describe, it, expect, vi } from 'vitest';

vi.mock('../../main/resources/static/js/utils.js', () => ({
    escapeHtml: (s) => s,
}));

async function flushPromises() {
    for (let i = 0; i < 10; i++) await Promise.resolve();
}

const PAGE_HTML = `
<table><tbody>
  <tr>
    <td class="ip-cell" data-ip="1.2.3.4">1.2.3.4</td>
  </tr>
  <tr>
    <td class="ip-cell" data-ip="5.6.7.8">5.6.7.8</td>
  </tr>
</tbody></table>
`;

const SAMPLE_INFO = { ip: '1.2.3.4', hostname: 'host.example.com', org: 'AS1 Acme', city: 'Paris', country: 'FR' };

let init;
beforeEach(async () => {
    document.body.innerHTML = PAGE_HTML;
    vi.clearAllMocks();
    ({ init } = await import('../../main/resources/static/js/pages/ua-requests.js'));
    init();
});

describe('ua-requests IP lookup', () => {
    it('calls /api/ip-info/{ip} on click', async () => {
        const fetchMock = vi.fn().mockResolvedValue({ json: () => Promise.resolve(SAMPLE_INFO) });
        vi.stubGlobal('fetch', fetchMock);

        document.querySelector('.ip-cell').click();
        await flushPromises();

        expect(fetchMock).toHaveBeenCalledWith('/api/ip-info/1.2.3.4');
    });

    it('renders hostname and org after successful lookup', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ json: () => Promise.resolve(SAMPLE_INFO) }));

        const cell = document.querySelector('.ip-cell');
        cell.click();
        await flushPromises();

        const info = cell.querySelector('div');
        expect(info).not.toBeNull();
        expect(info.textContent).toContain('AS1 Acme');
        expect(info.textContent).toContain('host.example.com');
    });

    it('does not call fetch again on second click', async () => {
        const fetchMock = vi.fn().mockResolvedValue({ json: () => Promise.resolve(SAMPLE_INFO) });
        vi.stubGlobal('fetch', fetchMock);

        const cell = document.querySelector('.ip-cell');
        cell.click();
        await flushPromises();
        cell.click();
        await flushPromises();

        expect(fetchMock).toHaveBeenCalledTimes(1);
    });
});
