import { expect, test } from '@playwright/test';
import { ACME, TEST_USER, mockApi } from '#lib/testing/mock-api.ts';

test('setting up the first company takes a name, with Greece and its currency pre-selected', async ({
  page,
  baseURL
}) => {
  await mockApi(page, baseURL!, { user: TEST_USER });
  await page.goto('/onboarding');

  await expect(page.getByLabel('Country')).toHaveValue('GR');
  await expect(page.getByText('Currency: Euro (EUR)')).toBeVisible();
  await page.getByLabel('Company name').fill('Acme');
  await page.getByRole('button', { name: 'Create company' }).click();

  await expect(page).toHaveURL(/\/c\/0NEWCOMPANY01$/);
  await expect(page.getByRole('button', { name: 'Acme' })).toBeVisible();
});

test('a country without invoicing support says so, before and after', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER });
  await page.goto('/onboarding');

  await page.getByLabel('Country').selectOption('DE');
  await expect(page.getByRole('status')).toHaveText("Invoicing isn't available in Germany yet.");
  await page.getByLabel('Company name').fill('Acme GmbH');
  await page.getByRole('button', { name: 'Create company' }).click();

  await expect(page.getByText("Invoicing isn't available in Germany yet.")).toBeVisible();
});

test('the backend refusing the name shows on the field', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER });
  await page.goto('/onboarding');

  // Spaces get past the browser's required check, but not the backend's
  await page.getByLabel('Company name').fill('   ');
  await page.getByRole('button', { name: 'Create company' }).click();

  await expect(page.getByLabel('Company name')).toHaveAccessibleDescription('Enter a company name.');
});

test('a further company starts from the switcher, and can be cancelled', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [ACME] });
  await page.goto(`/c/${ACME.company.id}`);

  await page.getByRole('button', { name: 'Acme' }).click();
  await page.getByRole('link', { name: 'New company' }).click();

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('New company');
  await page.getByRole('link', { name: 'Cancel' }).click();
  await expect(page).toHaveURL(`/c/${ACME.company.id}`);
});
