import { expect, test } from '@playwright/test';
import { TEST_USER, mockApi } from '#lib/testing/mock-api.ts';

test('the full flow: sent to login from /, sign in through the link, and back to /', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!);
  await page.goto('/');
  await page.getByLabel('Email').fill(TEST_USER.email);
  await page.getByRole('button', { name: 'Email me a sign-in link' }).click();
  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Check your inbox');

  // Opening the emailed link
  await page.goto('/auth/verify?token=abc');

  await expect(page).toHaveURL('/');
  await expect(page.getByText(`Signed in as ${TEST_USER.email}.`)).toBeVisible();
});

test('an invalid or expired link says so', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { verify: 'invalid' });

  await page.goto('/auth/verify?token=abc');

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('This sign-in link is invalid or has expired');
  await expect(page.getByRole('link', { name: 'Request a new link' })).toHaveAttribute('href', '/login');
});

test('a locked account says so', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { verify: 'locked' });

  await page.goto('/auth/verify?token=abc');

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('This account is locked');
});

test('a link without a token is invalid', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!);

  await page.goto('/auth/verify');

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('This sign-in link is invalid or has expired');
});

test('the token is removed from the address bar', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { verify: 'invalid' });

  await page.goto('/auth/verify?token=abc');

  await expect(page).toHaveURL('/auth/verify');
});
