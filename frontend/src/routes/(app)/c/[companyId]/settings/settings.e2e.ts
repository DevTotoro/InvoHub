import { expect, test, type Page } from '@playwright/test';
import { ACME, GLOBEX, LOGO_PNG, TEST_USER, mockApi } from '#lib/testing/mock-api.ts';

const asMember = { ...ACME, role: 'MEMBER' as const };
const tabs = (page: Page) => page.getByRole('navigation', { name: 'Company' });
const section = (page: Page, name: string) => page.getByRole('region', { name });

test('the owner sees what invoicing still needs, and goes to settings from Overview', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [ACME] });
  await page.goto(`/c/${ACME.company.id}`);

  const setup = page.getByRole('region', { name: 'Invoicing setup' });
  await expect(setup.getByRole('listitem')).toHaveText([
    /Tax ID.*Needed/,
    /Business details.*Needed/,
    /myDATA connection.*Coming soon/
  ]);
  await expect(tabs(page).getByRole('link', { name: 'Settings (Needed)' })).toBeVisible();

  await setup.getByRole('link', { name: 'Go to settings' }).click();

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Settings');
  await expect(section(page, 'Business details')).toContainText('Needed');
});

test('filling in the business details completes the setup', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [ACME] });
  await page.goto(`/c/${ACME.company.id}/settings`);
  const details = section(page, 'Business details');

  await details.getByLabel('Tax ID (ΑΦΜ)').fill('094014202');
  await details.getByRole('button', { name: 'Save' }).click();
  await expect(details.getByLabel('Tax ID (ΑΦΜ)')).toHaveAccessibleDescription('Check this tax ID for typos.');

  // The code, not its description
  await expect(details.getByLabel('Main activity code (ΚΑΔ)')).toHaveAttribute('placeholder', 'e.g. 47.54.10.01');
  await details.getByLabel('Tax ID (ΑΦΜ)').fill('094014201');
  await details.getByLabel('Main activity code (ΚΑΔ)').fill('ΗΛΕΚΤΡΟΛΟΓΙΚΟ ΥΛΙΚΟ');
  await details.getByRole('button', { name: 'Save' }).click();
  await expect(details.getByLabel('Main activity code (ΚΑΔ)')).toHaveAccessibleDescription(
    'Enter a code of 2 to 8 digits.'
  );

  await details.getByLabel('Legal name').fill('Acme ΙΚΕ');
  await details.getByLabel('Street address').fill('Ερμού 1');
  await details.getByLabel('City').fill('Αθήνα');
  await details.getByLabel('Postal code').fill('10563');
  await details.getByLabel('Tax ID (ΑΦΜ)').fill('094014201');
  await details.getByLabel('Tax office (ΔΟΥ)').fill("Α' Αθηνών");
  await details.getByLabel('Main activity code (ΚΑΔ)').fill('62.01');
  await details.getByRole('button', { name: 'Save' }).click();

  await expect(details.getByRole('status')).toHaveText('Saved.');
  await expect(details).toContainText('✓ Done');
  await expect(details.getByLabel('Main activity code (ΚΑΔ)')).toHaveValue('6201');
  // Only myDATA is left, and it can't be done yet
  await expect(tabs(page).getByRole('link', { name: 'Settings', exact: true })).toBeVisible();

  await tabs(page).getByRole('link', { name: 'Overview' }).click();
  await expect(page.getByRole('region', { name: 'Invoicing setup' })).toHaveCount(0);
});

test('changing the country asks first, as it clears the tax ID', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, {
    user: TEST_USER,
    companies: [ACME],
    settings: { [ACME.company.id]: { taxId: '094014201' } }
  });
  await page.goto(`/c/${ACME.company.id}/settings`);
  const company = section(page, 'Company');

  await company.getByLabel('Country').selectOption('DE');
  await expect(company.getByLabel('Country')).toHaveAccessibleDescription('Currency: Euro (EUR)');
  await company.getByRole('button', { name: 'Save' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Cancel' }).click();
  await expect(section(page, 'Business details').getByLabel('Tax ID (ΑΦΜ)')).toHaveValue('094014201');

  await company.getByLabel('Company name').fill('Acme GmbH');
  await company.getByRole('button', { name: 'Save' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Change country' }).click();

  await expect(page.getByRole('button', { name: 'Acme GmbH' })).toBeVisible();
  await expect(section(page, 'Business details').getByLabel('Tax ID', { exact: true })).toHaveValue('');
  await expect(section(page, 'Business details').getByLabel('Tax office (ΔΟΥ)')).toHaveCount(0);
  await expect(section(page, 'myDATA')).toHaveCount(0);
});

test('bank accounts are added, checked and removed', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [ACME] });
  await page.goto(`/c/${ACME.company.id}/settings`);
  const bank = section(page, 'Bank accounts');
  await expect(bank).toContainText('No bank accounts yet.');

  await bank.getByLabel('Bank').fill('Eurobank');
  await bank.getByLabel('IBAN').fill('GR16 0110 1250 0000 0001 2300 696');
  await bank.getByRole('button', { name: 'Add account' }).click();
  await expect(bank.getByLabel('IBAN')).toHaveAccessibleDescription('Enter a valid IBAN.');

  await bank.getByLabel('IBAN').fill('gr16 0110 1250 0000 0001 2300 695');
  await bank.getByRole('button', { name: 'Add account' }).click();
  await expect(bank.getByRole('listitem')).toHaveText([/Eurobank\s*GR16 0110 1250 0000 0001 2300 695/]);
  await expect(bank.getByLabel('IBAN')).toHaveValue('');

  await bank.getByRole('button', { name: 'Remove GR16 0110 1250 0000 0001 2300 695' }).click();
  await page
    .getByRole('dialog', { name: 'Remove the account GR16 0110 1250 0000 0001 2300 695?' })
    .getByRole('button', { name: 'Remove' })
    .click();
  await expect(bank).toContainText('No bank accounts yet.');
});

test('a logo is uploaded, refused when not an image, and removed', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [ACME] });
  await page.goto(`/c/${ACME.company.id}/settings`);
  const logo = section(page, 'Logo');

  await logo
    .getByLabel('Upload logo')
    .setInputFiles({ name: 'logo.svg', mimeType: 'image/svg+xml', buffer: Buffer.from('<svg/>') });
  await expect(logo.getByRole('alert')).toHaveText('Use a PNG, JPEG or WebP image.');
  // Claiming to be a PNG isn't enough: what was uploaded is checked
  await logo
    .getByLabel('Upload logo')
    .setInputFiles({ name: 'fake.png', mimeType: 'image/png', buffer: Buffer.from('<svg/>') });
  await expect(logo.getByRole('alert')).toHaveText('Use a PNG, JPEG or WebP image.');

  await logo.getByLabel('Upload logo').setInputFiles({ name: 'logo.png', mimeType: 'image/png', buffer: LOGO_PNG });
  // Loaded from storage, not from InvoHub
  await expect(logo.getByRole('img', { name: 'Acme logo' })).toHaveAttribute('src', /\/mock-storage\/logos\//);
  await expect(logo.getByRole('img', { name: 'Acme logo' })).toHaveJSProperty('naturalWidth', 1);
  await expect(logo.getByRole('alert')).toHaveCount(0);

  await logo.getByLabel('Replace logo').setInputFiles({
    name: 'big.png',
    mimeType: 'image/png',
    buffer: Buffer.concat([LOGO_PNG, Buffer.alloc(1024 * 1024)])
  });
  await expect(logo.getByRole('alert')).toHaveText('Use an image up to 1 MB.');

  await logo.getByRole('button', { name: 'Remove logo' }).click();
  await page.getByRole('dialog', { name: 'Remove the logo?' }).getByRole('button', { name: 'Remove logo' }).click();
  await expect(logo.getByRole('img')).toHaveCount(0);
});

test('members see no settings', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [asMember] });
  await page.goto(`/c/${ACME.company.id}`);

  await expect(tabs(page).getByRole('link')).toHaveText(['Overview', 'Contacts', 'Team']);
  await expect(page.getByRole('region', { name: 'Invoicing setup' })).toHaveCount(0);

  await page.goto(`/c/${ACME.company.id}/settings`);
  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Page not found');
});

test("a country without invoicing has no checklist, and a read-only company can't be changed", async ({
  page,
  baseURL
}) => {
  const readOnly = { ...GLOBEX, company: { ...GLOBEX.company, status: 'READ_ONLY' as const } };
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [readOnly] });
  await page.goto(`/c/${GLOBEX.company.id}`);
  await expect(page.getByRole('region', { name: 'Invoicing setup' })).toHaveCount(0);

  await tabs(page).getByRole('link', { name: 'Settings', exact: true }).click();

  await expect(page.getByRole('main')).toContainText('This company is read-only.');
  await expect(section(page, 'Company').getByLabel('Company name')).toBeDisabled();
  await expect(section(page, 'Business details')).not.toContainText('Needed');
});

test('in Greek', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: { ...TEST_USER, language: 'EL' }, companies: [ACME] });
  await page.goto(`/c/${ACME.company.id}/settings`);

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Ρυθμίσεις');
  const details = section(page, 'Στοιχεία επιχείρησης');
  await expect(details).toContainText('Εκκρεμεί');

  await details.getByLabel('ΑΦΜ').fill('094014202');
  await details.getByRole('button', { name: 'Αποθήκευση' }).click();

  await expect(details.getByLabel('ΑΦΜ')).toHaveAccessibleDescription('Ελέγξτε τον ΑΦΜ για λάθη.');
});
