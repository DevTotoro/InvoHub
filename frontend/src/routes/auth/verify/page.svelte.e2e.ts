import { expect, test } from '@playwright/test';

test('a link without a token is reported as invalid', async ({ page }) => {
  await page.goto('/auth/verify');

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('This sign-in link is invalid or has expired');
});

test('the token is removed from the address bar', async ({ page }) => {
  await page.goto('/auth/verify?token=abc');

  await expect(page).toHaveURL(/\/auth\/verify$/);
});
