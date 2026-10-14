import { expect, test, type Page } from '@playwright/test';
import type { Contact } from '#lib/api/contacts.ts';
import { ACME, TEST_USER, mockApi } from '#lib/testing/mock-api.ts';

const contact = (name: string, extra: Partial<Contact> = {}): Contact => ({
  id: `0C${name.replace(/\W/g, '').toUpperCase().padEnd(11, 'X').slice(0, 11)}`,
  kind: 'BUSINESS',
  name,
  country: 'GR',
  taxId: null,
  email: null,
  phone: null,
  address: { street: null, city: null, postalCode: null },
  notes: null,
  archivedAt: null,
  ...extra
});
const asViewer = { ...ACME, role: 'VIEWER' as const };
const list = (page: Page) => page.getByRole('list', { name: 'Contacts' }).getByRole('listitem');

test('adding a contact checks its tax ID, then shows it', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [ACME] });
  await page.goto(`/c/${ACME.company.id}`);

  await page.getByRole('navigation', { name: 'Company' }).getByRole('link', { name: 'Contacts' }).click();
  await expect(page.getByText('No contacts yet.')).toBeVisible();
  await page.getByRole('link', { name: 'New contact' }).click();

  await expect(page.getByRole('radio', { name: 'Business' })).toBeChecked();
  await expect(page.getByLabel('Country')).toHaveValue('GR');
  await page.getByLabel('Name').fill('Papadopoulos ΑΕ');
  await page.getByLabel('Tax ID (ΑΦΜ)').fill('094014202');
  await page.getByRole('button', { name: 'Add contact' }).click();
  await expect(page.getByLabel('Tax ID (ΑΦΜ)')).toHaveAccessibleDescription('Check this tax ID for typos.');

  await page.getByLabel('Tax ID (ΑΦΜ)').fill('EL 094 014 201');
  await page.getByLabel('Email').fill('info@papadopoulos.gr');
  await page.getByRole('button', { name: 'Add contact' }).click();

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Papadopoulos ΑΕ');
  await expect(page.getByLabel('Tax ID (ΑΦΜ)')).toHaveValue('094014201');
  // Still under the Contacts tab
  await expect(
    page.getByRole('navigation', { name: 'Company' }).getByRole('link', { name: 'Contacts' })
  ).toHaveAttribute('aria-current', 'page');
  await page.getByRole('link', { name: 'All contacts' }).click();
  await expect(list(page)).toHaveText([/Papadopoulos ΑΕ.*094014201 · info@papadopoulos.gr/]);
});

test('an individual from abroad has a plain tax ID label', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [ACME] });
  await page.goto(`/c/${ACME.company.id}/contacts/new`);

  await page.getByRole('radio', { name: 'Individual' }).check();
  await page.getByLabel('Country').selectOption('DE');
  await expect(page.getByLabel('Tax ID', { exact: true })).toBeVisible();
  await page.getByLabel('Name').fill('Hans Müller');
  await page.getByRole('button', { name: 'Add contact' }).click();

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Hans Müller');
  await expect(page.getByRole('radio', { name: 'Individual' })).toBeChecked();
});

test('searching narrows the list, and the search stays in the address', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, {
    user: TEST_USER,
    companies: [ACME],
    contacts: {
      [ACME.company.id]: [
        contact('Papadopoulos ΑΕ', { taxId: '094014201' }),
        contact('Alexiou', { email: 'nikos@example.com' })
      ]
    }
  });
  await page.goto(`/c/${ACME.company.id}/contacts`);
  await expect(list(page)).toHaveText([/Alexiou/, /Papadopoulos/]);

  await page.getByRole('searchbox', { name: 'Search contacts' }).fill('nikos');

  await expect(list(page)).toHaveText([/Alexiou/]);
  await expect(page).toHaveURL(/\?q=nikos/);
  await expect(page.getByRole('searchbox', { name: 'Search contacts' })).toBeFocused();
  await page.reload();
  await expect(list(page)).toHaveText([/Alexiou/]);

  await page.getByRole('searchbox', { name: 'Search contacts' }).fill('nobody');
  await expect(page.getByText('No contacts match.')).toBeVisible();
});

test('editing saves, and archiving moves the contact to Archived, where it can be restored', async ({
  page,
  baseURL
}) => {
  const papadopoulos = contact('Papadopoulos ΑΕ');
  await mockApi(page, baseURL!, {
    user: TEST_USER,
    companies: [ACME],
    contacts: { [ACME.company.id]: [papadopoulos] }
  });
  await page.goto(`/c/${ACME.company.id}/contacts/${papadopoulos.id}`);

  await page.getByLabel('City').fill('Αθήνα');
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByRole('status')).toHaveText('Saved.');

  await page.getByRole('button', { name: 'Archive' }).click();
  await page.getByRole('dialog', { name: 'Archive Papadopoulos ΑΕ?' }).getByRole('button', { name: 'Archive' }).click();
  await expect(page.getByText('This contact is archived.')).toBeVisible();

  await page.getByRole('link', { name: 'All contacts' }).click();
  await expect(page.getByText('No contacts yet.')).toBeVisible();
  await page.getByRole('link', { name: 'Archived' }).click();
  await expect(list(page)).toHaveText([/Papadopoulos ΑΕ.*Αθήνα/]);

  await list(page).getByRole('link').click();
  await page.getByRole('button', { name: 'Restore' }).click();
  await expect(page.getByText('This contact is archived.')).toHaveCount(0);
});

test('viewers see contacts but change nothing', async ({ page, baseURL }) => {
  const papadopoulos = contact('Papadopoulos ΑΕ');
  await mockApi(page, baseURL!, {
    user: TEST_USER,
    companies: [asViewer],
    contacts: { [ACME.company.id]: [papadopoulos] }
  });
  await page.goto(`/c/${ACME.company.id}/contacts`);

  await expect(page.getByRole('link', { name: 'New contact' })).toHaveCount(0);
  await list(page).getByRole('link').click();

  await expect(page.getByLabel('Name')).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Save' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Archive' })).toHaveCount(0);

  await page.goto(`/c/${ACME.company.id}/contacts/new`);
  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Page not found');
});

test('a contact that no longer exists is not found', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [ACME] });

  await page.goto(`/c/${ACME.company.id}/contacts/0GONEGONEGONE`);

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Page not found');
});

test('in Greek', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: { ...TEST_USER, language: 'EL' }, companies: [ACME] });
  await page.goto(`/c/${ACME.company.id}/contacts/new`);

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Νέα επαφή');
  // Only spaces: past the browser's own check, refused by InvoHub
  await page.getByLabel('Όνομα ή επωνυμία').fill('   ');
  await page.getByRole('button', { name: 'Προσθήκη επαφής' }).click();

  await expect(page.getByLabel('Όνομα ή επωνυμία')).toHaveAccessibleDescription('Εισαγάγετε όνομα.');
});
