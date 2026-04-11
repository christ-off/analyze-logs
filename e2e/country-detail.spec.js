// @ts-check
import { test, expect } from '@playwright/test';

const URL = '/country-detail?country=US&from=2026-04-05&to=2026-04-11';

test.describe('Country Detail', () => {

  test('has correct title and country name', async ({ page }) => {
    await page.goto(URL);
    await expect(page).toHaveTitle(/Country Detail/);
    await expect(page.locator('.cf-detail-title')).toHaveText('United States');
  });

  test('shows date range in subtitle', async ({ page }) => {
    await page.goto(URL);
    const subtitle = page.locator('.cf-detail-subtitle');
    await expect(subtitle).toContainText('2026-04-05');
    await expect(subtitle).toContainText('2026-04-11');
  });

  test('back button returns to dashboard', async ({ page }) => {
    await page.goto(URL);
    await page.getByRole('link', { name: '← Back' }).click();
    await expect(page).toHaveTitle('CloudFront Dashboard');
  });

  test('all chart canvases are present', async ({ page }) => {
    await page.goto(URL);
    for (const id of ['chartUaNames', 'chartResultTypes', 'chartUriStems', 'chartRequestsPerDay']) {
      await expect(page.locator(`#${id}`)).toBeAttached();
    }
  });

  test('all chart API calls succeed', async ({ page }) => {
    const endpoints = [
      'country-detail/ua-split', 'country-detail/result-types',
      'country-detail/url-split', 'country-detail/requests-per-day',
    ];
    const promises = endpoints.map(ep =>
      page.waitForResponse(r => r.url().includes(`/api/${ep}`) && r.status() === 200)
    );
    await page.goto(URL);
    await Promise.all(promises);
  });

  test('preset range link stays on country-detail', async ({ page }) => {
    await page.goto(URL);
    await page.getByRole('link', { name: '7 days' }).click();
    await expect(page).toHaveURL(/\/country-detail\?.*country=US/);
    await expect(page).toHaveURL(/range=7d/);
  });

});
