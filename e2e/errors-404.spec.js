// @ts-check
import { test, expect } from '@playwright/test';

test.describe('404 & Errors', () => {

  test('has correct title and navbar', async ({ page }) => {
    await page.goto('/errors-404');
    await expect(page).toHaveTitle('404 & Errors');
    await expect(page.getByRole('link', { name: '404 & Errors' })).toBeVisible();
  });

  test('shows date range toolbar', async ({ page }) => {
    await page.goto('/errors-404');
    await expect(page.getByRole('link', { name: 'Today' })).toBeVisible();
    await expect(page.getByRole('link', { name: '7 days' })).toBeVisible();
    await expect(page.getByRole('link', { name: '30 days' })).toBeVisible();
    await expect(page.getByRole('link', { name: '3 months' })).toBeVisible();
  });

  test('uris API call succeeds and table renders without error', async ({ page }) => {
    const response = page.waitForResponse(r => r.url().includes('/api/errors-404/uris') && r.status() === 200);
    await page.goto('/errors-404?range=1d');
    await response;

    await expect(page.locator('#errors404Table')).not.toContainText('Failed to load data');
  });

  test('table shows only uri and count columns', async ({ page }) => {
    const response = page.waitForResponse(r => r.url().includes('/api/errors-404/uris') && r.status() === 200);
    await page.goto('/errors-404?range=7d');
    await response;

    const headers = page.locator('table thead th');
    await expect(headers).toHaveCount(2);
    await expect(headers.nth(0)).toHaveText('URI');
    await expect(headers.nth(1)).toHaveText('Count');
  });

});
