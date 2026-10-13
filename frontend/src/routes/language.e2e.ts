import { expect, test } from '@playwright/test';
import { ACME, TEST_USER, mockApi } from '#lib/testing/mock-api.ts';

test.describe('in a Greek browser', () => {
  test.use({ locale: 'el-GR' });

  test('InvoHub starts in Greek, and signing in asks for Greek emails', async ({ page, baseURL }) => {
    const api = await mockApi(page, baseURL!);

    await page.goto('/login');
    await expect(page.locator('html')).toHaveAttribute('lang', 'el');
    await expect(page.getByRole('heading', { level: 2 })).toHaveText('Σύνδεση');

    await page.getByLabel('Email').fill(TEST_USER.email);
    await page.getByRole('button', { name: 'Στείλτε μου σύνδεσμο σύνδεσης' }).click();

    await expect(page.getByRole('heading', { level: 2 })).toHaveText('Ελέγξτε τα εισερχόμενά σας');
    expect(api.authCalls('/api/auth/request')).toEqual([{ email: TEST_USER.email, language: 'EL', redirectTo: '/' }]);
  });

  test('a first sign-in creates the account in Greek', async ({ page, baseURL }) => {
    const api = await mockApi(page, baseURL!, { companies: [ACME], accountLanguage: 'EL' });

    await page.goto('/auth/verify?token=abc');
    await page.getByRole('button', { name: 'Συνέχεια' }).click();

    await expect(page.getByRole('heading', { level: 2 })).toHaveText(`Καλώς ήρθατε, ${TEST_USER.email}`);
    expect(api.authCalls('/api/auth/verify')).toEqual([{ token: 'abc', language: 'EL' }]);
  });
});

test('picking a language while signed out switches to it and remembers it', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!);
  await page.goto('/login');
  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Sign in');

  await page.getByRole('combobox', { name: 'Language' }).selectOption('el');

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Σύνδεση');
  await page.reload();
  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Σύνδεση');
});

test("signed in, InvoHub follows the account's language", async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: { ...TEST_USER, language: 'EL' }, companies: [ACME] });

  await page.goto(`/c/${ACME.company.id}`);

  await expect(page.getByRole('heading', { level: 2 })).toHaveText(`Καλώς ήρθατε, ${TEST_USER.email}`);
  await expect(page.locator('html')).toHaveAttribute('lang', 'el');
});

test('the account language wins over one picked before signing in', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { companies: [ACME], accountLanguage: 'EN' });
  await page.goto('/login');
  await page.getByRole('combobox', { name: 'Language' }).selectOption('el');
  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Σύνδεση');

  await page.goto('/auth/verify?token=abc');
  await page.getByRole('button', { name: 'Συνέχεια' }).click();

  await expect(page.getByRole('heading', { level: 2 })).toHaveText(`Welcome, ${TEST_USER.email}`);
});

test('signed in, picking a language saves it on the account', async ({ page, baseURL }) => {
  const api = await mockApi(page, baseURL!, { user: TEST_USER, companies: [ACME] });
  await page.goto(`/c/${ACME.company.id}`);
  await expect(page.getByRole('heading', { level: 2 })).toHaveText(`Welcome, ${TEST_USER.email}`);

  await page.getByRole('combobox', { name: 'Language' }).selectOption('el');

  await expect(page.getByRole('heading', { level: 2 })).toHaveText(`Καλώς ήρθατε, ${TEST_USER.email}`);
  expect(api.language()).toBe('EL');
  await page.getByRole('button', { name: 'Λογαριασμός' }).click();
  await expect(page.getByRole('button', { name: 'Αποσύνδεση' })).toBeVisible();
});

test('country names and the currency follow the language', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: { ...TEST_USER, language: 'EL' } });

  await page.goto('/onboarding');

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Δημιουργήστε την εταιρεία σας');
  await expect(page.getByLabel('Χώρα')).toHaveValue('GR');
  await expect(page.getByRole('option', { name: 'Ελλάδα' })).toBeAttached();
  await expect(page.getByText('Νόμισμα: Ευρώ (EUR)')).toBeVisible();
});
