import { expect, test, type Page } from '@playwright/test';
import { ACME, TEST_USER, mockApi, person } from '#lib/testing/mock-api.ts';

const john = person('john@example.com', 'ADMIN');
const maria = person('maria@example.com', 'VIEWER');
const asViewer = { ...ACME, role: 'VIEWER' as const };
const team = (page: Page) => page.getByRole('list', { name: /^(Team|Ομάδα)$/ }).getByRole('listitem');

test('the owner sees the team and can change roles', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [ACME], people: { [ACME.company.id]: [john, maria] } });
  await page.goto(`/c/${ACME.company.id}`);

  await page.getByRole('navigation', { name: 'Company' }).getByRole('link', { name: 'Team' }).click();

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Team');
  await expect(team(page)).toHaveText([/jane@example.com \(you\).*Owner/, /john@example.com/, /maria@example.com/]);
  await expect(page.getByRole('combobox', { name: `Role of ${TEST_USER.email}` })).toHaveCount(0);

  await page.getByRole('combobox', { name: 'Role of maria@example.com' }).selectOption('MEMBER');

  await page.reload();
  await expect(page.getByRole('combobox', { name: 'Role of maria@example.com' })).toHaveValue('MEMBER');
  await expect(page.getByRole('button', { name: 'Leave company' })).toHaveCount(0);
});

test('removing someone asks first', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [ACME], people: { [ACME.company.id]: [john, maria] } });
  await page.goto(`/c/${ACME.company.id}/members`);

  page.once('dialog', (dialog) => dialog.dismiss());
  await page.getByRole('button', { name: 'Remove maria@example.com' }).click();
  await expect(page.getByText('maria@example.com')).toBeVisible();

  page.once('dialog', (dialog) => {
    expect(dialog.message()).toBe('Remove maria@example.com from Acme? They lose access right away.');
    return dialog.accept();
  });
  await page.getByRole('button', { name: 'Remove maria@example.com' }).click();

  await expect(page.getByText('maria@example.com')).toHaveCount(0);
});

test('a viewer sees the roles but changes nothing, and can leave', async ({ page, baseURL }) => {
  const owner = person('owner@example.com', 'OWNER');
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [asViewer], people: { [ACME.company.id]: [owner] } });
  await page.goto(`/c/${ACME.company.id}/members`);

  await expect(team(page).first()).toContainText('owner@example.com');
  await expect(page.getByRole('main').getByRole('combobox')).toHaveCount(0);
  await expect(page.getByRole('button', { name: /Remove/ })).toHaveCount(0);

  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: 'Leave company' }).click();

  // No companies left
  await expect(page).toHaveURL('/onboarding');
});

test('a failed change is explained, and the list shows how things really are', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [ACME], people: { [ACME.company.id]: [john] } });
  await page.goto(`/c/${ACME.company.id}/members`);
  // Someone else removed John in the meantime
  await page.route('**/api/graphql', async (route) => {
    if (!route.request().postData()?.includes('RemoveMember')) return route.fallback();
    return route.fulfill({
      json: { data: null, errors: [{ message: 'gone', extensions: { code: 'member_not_found' } }] }
    });
  });

  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: 'Remove john@example.com' }).click();

  await expect(page.getByRole('alert')).toHaveText('This person is no longer in the company.');
});

test('in Greek', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, {
    user: { ...TEST_USER, language: 'EL' },
    companies: [ACME],
    people: { [ACME.company.id]: [maria] }
  });

  await page.goto(`/c/${ACME.company.id}/members`);

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Ομάδα');
  await expect(team(page).first()).toContainText('Ιδιοκτήτης');
  await expect(page.getByRole('combobox', { name: 'Ρόλος για maria@example.com' })).toBeVisible();
});
