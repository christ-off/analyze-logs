// @ts-check
import { test, expect } from '@playwright/test';

test.describe('Dashboard', () => {

  test('has correct title and navbar', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveTitle('CloudFront Dashboard');
    await expect(page.getByRole('link', { name: 'CloudFront Logs' })).toBeVisible();
  });

  test('shows date range toolbar', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('link', { name: 'Today' })).toBeVisible();
    await expect(page.getByRole('link', { name: '7 days' })).toBeVisible();
    await expect(page.getByRole('link', { name: '30 days' })).toBeVisible();
    await expect(page.getByRole('link', { name: '3 months' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Apply' })).toBeVisible();
  });

  test('all chart canvases are present', async ({ page }) => {
    await page.goto('/');
    for (const id of [
      'chartUaNames', 'chartCountries', 'chartTopUrls',
      'chartUaGroups', 'chartReferers', 'chartRequestsPerDay',
    ]) {
      await expect(page.locator(`#${id}`)).toBeAttached();
    }
  });

  test('all chart API calls succeed', async ({ page }) => {
    const endpoints = [
      'ua-groups', 'ua-names-split', 'countries',
      'top-urls-split', 'referers', 'requests-per-day',
    ];
    const promises = endpoints.map(ep =>
      page.waitForResponse(r => r.url().includes(`/api/${ep}`) && r.status() === 200)
    );
    await page.goto('/');
    await Promise.all(promises);
  });

  test('preset range link navigates with correct param', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('link', { name: '30 days' }).click();
    await expect(page).toHaveURL(/range=30d/);
  });

});
