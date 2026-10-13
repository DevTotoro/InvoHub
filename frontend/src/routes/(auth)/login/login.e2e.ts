import { expect, test } from '@playwright/test';
import { ACME, TEST_USER, mockApi } from '#lib/testing/mock-api.ts';

test('requesting a link shows where it was sent', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!);
  await page.goto('/login');
  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Sign in');

  await page.getByLabel('Email').fill('jane@example.com');
  await page.getByRole('button', { name: 'Email me a sign-in link' }).click();

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Check your inbox');
  await expect(page.getByText('jane@example.com')).toBeVisible();
});

test('signed-in users skip the login page', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [ACME] });

  await page.goto('/login');

  await expect(page).toHaveURL(`/c/${ACME.company.id}`);
});

test('an off-site redirect target is ignored', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [ACME] });

  await page.goto('/login?redirectTo=//evil.example');

  await expect(page).toHaveURL(`/c/${ACME.company.id}`);
});

test('the sign-in link leads back to the page the person was going to', async ({ page, baseURL }) => {
  const api = await mockApi(page, baseURL!);

  await page.goto(`/c/${ACME.company.id}/members`);
  await page.getByLabel('Email').fill(TEST_USER.email);
  await page.getByRole('button', { name: 'Email me a sign-in link' }).click();
  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Check your inbox');

  expect(api.authCalls('/api/auth/request')).toEqual([
    { email: TEST_USER.email, language: 'EN', redirectTo: `/c/${ACME.company.id}/members` }
  ]);
});
