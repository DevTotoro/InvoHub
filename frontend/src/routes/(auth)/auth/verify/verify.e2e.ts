import { expect, test } from '@playwright/test';
import { ACME, TEST_USER, invitation, mockApi } from '#lib/testing/mock-api.ts';

test('the full flow: sent to login from /, sign in through the link, and back in', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { companies: [ACME] });
  await page.goto('/');
  await page.getByLabel('Email').fill(TEST_USER.email);
  await page.getByRole('button', { name: 'Email me a sign-in link' }).click();
  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Check your inbox');

  // Opening the emailed link, then confirming
  await page.goto('/auth/verify?token=abc');
  await page.getByRole('button', { name: 'Continue' }).click();

  await expect(page).toHaveURL(`/c/${ACME.company.id}`);
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

test('a link from an invitation email opens the invitations', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, {
    companies: [ACME],
    destination: 'INVITATIONS',
    invitations: [invitation(ACME.company, TEST_USER.email)]
  });

  await page.goto('/auth/verify?token=abc');
  await page.getByRole('button', { name: 'Continue' }).click();

  await expect(page).toHaveURL('/invitations');
  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Invitations');
});

test('an expired link offers a new one, sent to the same address', async ({ page, baseURL }) => {
  const api = await mockApi(page, baseURL!, { verify: 'expired' });

  await page.goto('/auth/verify?token=old-token');
  await page.getByRole('button', { name: 'Continue' }).click();
  await expect(page.getByRole('heading', { level: 2 })).toHaveText('This sign-in link has expired');
  await page.getByRole('button', { name: 'Send me a new link' }).click();

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Check your inbox');
  expect(api.authCalls('/api/auth/renew')).toEqual([{ token: 'old-token', language: 'EN' }]);
});
