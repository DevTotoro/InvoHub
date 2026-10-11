import { expect, test } from '@playwright/test';
import { TEST_USER, mockApi } from '#lib/testing/mock-api.ts';

test('requesting a link shows where it was sent', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!);
  await page.goto('/login');

  await page.getByLabel('Email').fill('jane@example.com');
  await page.getByRole('button', { name: 'Email me a sign-in link' }).click();

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Check your inbox');
  await expect(page.getByText('jane@example.com')).toBeVisible();
});

test('signed-in users skip the login page', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER });

  await page.goto('/login');

  await expect(page).toHaveURL('/');
});

test('an off-site redirect target is ignored', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER });

  await page.goto('/login?redirectTo=//evil.example');

  await expect(page).toHaveURL('/');
});
