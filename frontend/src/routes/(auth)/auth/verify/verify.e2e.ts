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

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('This link has expired');
  await expect(page.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login?redirectTo=%2F');
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

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('This link has expired');
});

test('the token is removed from the address bar', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { verify: 'invalid' });

  await page.goto('/auth/verify?token=abc');

  await expect(page).toHaveURL('/auth/verify');
});

test('a link goes where it says once signed in, e.g. the invitations', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { companies: [ACME], invitations: [invitation(ACME.company, TEST_USER.email)] });

  await page.goto('/auth/verify?token=abc&redirectTo=%2Finvitations');
  await page.getByRole('button', { name: 'Continue' }).click();

  await expect(page).toHaveURL('/invitations');
  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Invitations');
});

test('a link that no longer works offers to sign in, still going where it led', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { verify: 'invalid' });

  await page.goto('/auth/verify?token=old-token&redirectTo=%2Finvitations');
  await page.getByRole('button', { name: 'Continue' }).click();

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('This link has expired');
  await expect(page.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login?redirectTo=%2Finvitations');
});

test('signed in already, a link just carries on where it leads, without being used', async ({ page, baseURL }) => {
  const api = await mockApi(page, baseURL!, { user: TEST_USER, companies: [ACME] });

  await page.goto(`/auth/verify?token=abc&redirectTo=${encodeURIComponent(`/c/${ACME.company.id}/members`)}`);

  await expect(page).toHaveURL(`/c/${ACME.company.id}/members`);
  expect(api.authCalls('/api/auth/verify')).toHaveLength(0);
});

test('signed in already, a link without a destination goes home', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [ACME] });

  await page.goto('/auth/verify?token=used-long-ago');

  await expect(page).toHaveURL(`/c/${ACME.company.id}`);
});

test('a link pointing off the site goes home instead', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { companies: [ACME] });

  await page.goto('/auth/verify?token=abc&redirectTo=%2F%2Fevil.example');
  await page.getByRole('button', { name: 'Continue' }).click();

  await expect(page).toHaveURL(`/c/${ACME.company.id}`);
});
