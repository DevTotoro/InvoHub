import { expect, test } from '@playwright/test';
import { ACME, GLOBEX, TEST_USER, mockApi } from '#lib/testing/mock-api.ts';

test('signed-out visitors are sent to the login page', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!);

  await page.goto('/');

  await expect(page).toHaveURL('/login?redirectTo=%2F');
  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Sign in');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('InvoHub');
});

test('a user without companies is asked to set one up', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER });

  await page.goto('/');

  await expect(page).toHaveURL('/onboarding');
  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Set up your company');
});

test('a user with companies lands on one and can sign out', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [ACME] });

  await page.goto('/');
  await expect(page).toHaveURL(`/c/${ACME.company.id}`);
  await expect(page.getByRole('heading', { level: 2 })).toHaveText(`Welcome, ${TEST_USER.email}`);

  await page.getByRole('button', { name: 'Sign out' }).click();

  await expect(page).toHaveURL('/login');
  await expect(page.getByRole('button', { name: 'Sign out' })).toHaveCount(0);
});

test('/ returns to the company used last', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [ACME, GLOBEX] });
  await page.goto(`/c/${GLOBEX.company.id}`);
  await expect(page.getByRole('heading', { level: 2 })).toHaveText(`Welcome, ${TEST_USER.email}`);

  await page.goto('/');

  await expect(page).toHaveURL(`/c/${GLOBEX.company.id}`);
});

test('a user suspended while signed in is signed out', async ({ page, baseURL }) => {
  const api = await mockApi(page, baseURL!, { user: TEST_USER, companies: [ACME] });
  await page.goto(`/c/${ACME.company.id}`);
  await expect(page.getByRole('heading', { level: 2 })).toHaveText(`Welcome, ${TEST_USER.email}`);

  api.suspend();
  await page.reload();

  await expect(page).toHaveURL(`/login?redirectTo=${encodeURIComponent(`/c/${ACME.company.id}`)}`);
  await expect(page.getByRole('button', { name: 'Sign out' })).toHaveCount(0);
});

test('an unreachable API shows an error rather than the login page', async ({ page }) => {
  await page.route('**/api/**', (route) => route.abort());

  await page.goto('/');

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Something went wrong');
  await expect(page).toHaveURL('/');
});
