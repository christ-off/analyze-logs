import { beforeEach, describe, it, expect, vi } from 'vitest';

vi.mock('../../main/resources/static/js/utils.js', () => ({ escapeHtml: (s) => s }));
vi.mock('../../main/resources/static/js/ip-info.js', () => ({
    getIpInfo: vi.fn(() => Promise.resolve({ org: 'AS1 Acme', city: 'Paris', country: 'FR', hostname: 'host.example.com' })),
}));
vi.mock('../../main/resources/static/js/refresh.js', () => ({ initRefresh: vi.fn() }));

import { flushPromises } from './test-helpers.js';

describe('ip-requests header', () => {
    beforeEach(() => {
        document.body.innerHTML = '<div id="ipInfo" data-ip="1.2.3.4">Resolving…</div>';
    });

    it('shows the resolved IP info', async () => {
        const { init } = await import('../../main/resources/static/js/pages/ip-requests.js');
        init();
        await flushPromises();
        expect(document.getElementById('ipInfo').textContent).toBe('AS1 Acme · Paris, FR · host.example.com');
    });
});
