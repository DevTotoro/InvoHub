import { expect, test } from '@playwright/test';
import { TEST_USER, mockApi } from '#lib/testing/mock-api.ts';

test('the full flow: sent to login from /, sign in through the link, and back to /', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!);
  await page.goto('/');
  await page.getByLabel('Email').fill(TEST_USER.email);
  await page.getByRole('button', { name: 'Email me a sign-in link' }).click();
  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Check your inbox');

  // Opening the emailed link, then confirming
  await page.goto('/auth/verify?token=abc');
  await page.getByRole('button', { name: 'Continue' }).click();

  await expect(page).toHaveURL('/');
  await expect(page.getByRole('heading', { level: 2 })).toHaveText(`Welcome, ${TEST_USER.email}`);
});

test('an invalid or expired link says so', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { verify: 'invalid' });

  await page.goto('/auth/verify?token=abc');
  await page.getByRole('button', { name: 'Continue' }).click();

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('This sign-in link is invalid or has expired');
  await expect(page.getByRole('link', { name: 'Request a new link' })).toHaveAttribute('href', '/login');
});

test('a suspended account says so', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { verify: 'suspended' });

  await page.goto('/auth/verify?token=abc');
  await page.getByRole('button', { name: 'Continue' }).click();

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('This account is suspended');
});

test('opening the link does not sign in until the user continues', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!);
  const verifyCalls: string[] = [];
  page.on('request', (request) => {
    if (request.url().endsWith('/api/auth/verify')) verifyCalls.push(request.url());
  });

  await page.goto('/auth/verify?token=abc');
  await expect(page.getByRole('button', { name: 'Continue' })).toBeVisible();

  expect(verifyCalls).toHaveLength(0);
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
