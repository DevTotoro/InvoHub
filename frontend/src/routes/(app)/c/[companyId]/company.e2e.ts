import { expect, test } from '@playwright/test';
import { ACME, GLOBEX, TEST_USER, mockApi } from '#lib/testing/mock-api.ts';

test("a company that isn't the user's is not found", async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [ACME] });

  await page.goto('/c/0SOMEONEELSES');

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Page not found');
});

test('the switcher lists the companies and switches between them', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [ACME, GLOBEX] });
  await page.goto(`/c/${ACME.company.id}`);

  await page.getByRole('button', { name: 'Acme' }).click();
  const companies = page.getByRole('list', { name: 'Your companies' });
  await expect(companies.getByRole('link')).toHaveText(['Acme', 'Globex']);
  await expect(companies.getByRole('link', { name: 'Acme' })).toHaveAttribute('aria-current', 'page');

  await companies.getByRole('link', { name: 'Globex' }).click();

  await expect(page).toHaveURL(`/c/${GLOBEX.company.id}`);
  await expect(page.getByRole('button', { name: 'Globex' })).toBeVisible();
  await expect(companies).toBeHidden();
});
