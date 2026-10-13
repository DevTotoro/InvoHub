import { expect, test } from '@playwright/test';
import { ACME, GLOBEX, TEST_USER, mockApi, person } from '#lib/testing/mock-api.ts';

const maria = person('maria@example.com', 'ADMIN');
const asMember = { ...ACME, role: 'MEMBER' as const };
const closed = (c: typeof ACME) => ({ ...c, company: { ...c.company, closedAt: '2026-10-13T10:00:00Z' } });

test('the owner offers ownership to a member, and can cancel the offer', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [ACME], people: { [ACME.company.id]: [maria] } });
  await page.goto(`/c/${ACME.company.id}/members`);

  await page.getByRole('button', { name: 'Make maria@example.com the owner' }).click();
  const dialog = page.getByRole('dialog', { name: /Offer ownership of Acme to maria@example.com\?/ });
  await dialog.getByRole('button', { name: 'Offer ownership' }).click();

  await expect(page.getByRole('status')).toHaveText(/Ownership offered to maria@example.com · open until/);
  // One offer at a time
  await expect(page.getByRole('button', { name: /Make .* the owner/ })).toHaveCount(0);

  await page.getByRole('button', { name: 'Cancel offer' }).click();
  await expect(page.getByRole('status')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Make maria@example.com the owner' })).toBeVisible();
});

test('only the owner can offer ownership', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, {
    user: TEST_USER,
    companies: [{ ...ACME, role: 'ADMIN' }],
    people: { [ACME.company.id]: [person('owner@example.com', 'OWNER'), maria] }
  });
  await page.goto(`/c/${ACME.company.id}/members`);

  await expect(page.getByRole('combobox', { name: 'Role of maria@example.com' })).toBeVisible();
  await expect(page.getByRole('button', { name: /Make .* the owner/ })).toHaveCount(0);
});

test('the member sees the offer with their invitations, accepts, and owns the company', async ({ page, baseURL }) => {
  const offer = {
    id: '0OFFERACME001',
    company: ACME.company,
    from: 'owner@example.com',
    to: TEST_USER.email,
    expiresAt: '2026-10-20T10:00:00Z'
  };
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [asMember], ownershipOffers: [offer] });
  await page.goto(`/c/${ACME.company.id}`);
  await expect(page.getByRole('navigation', { name: 'Company' }).getByRole('link', { name: /Settings/ })).toHaveCount(
    0
  );

  await page.getByRole('button', { name: /Account, Invitations \(1\)/ }).click();
  await page.getByRole('link', { name: 'Invitations (1)' }).click();
  const item = page.getByRole('list', { name: 'Invitations' }).getByRole('listitem');
  await expect(item).toHaveText(/Acme.*Ownership offered by owner@example.com/);

  await item.getByRole('button', { name: 'Accept' }).click();

  await expect(page).toHaveURL(`/c/${ACME.company.id}`);
  await expect(page.getByRole('navigation', { name: 'Company' }).getByRole('link', { name: /Settings/ })).toBeVisible();
});

test('closing keeps the company, read-only, and the owner reopens it', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [ACME, GLOBEX] });
  await page.goto(`/c/${ACME.company.id}/settings`);

  await page.getByRole('button', { name: 'Close company' }).click();
  await page.getByRole('dialog', { name: 'Close Acme?' }).getByRole('button', { name: 'Close company' }).click();

  await expect(page.getByRole('status').filter({ hasText: 'This company is closed.' })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Company' }).getByLabel('Company name')).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Close company' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Acme' }).click();
  await expect(page.getByRole('list', { name: 'Your companies' }).getByRole('link')).toHaveText(['Globex']);
  await expect(page.getByRole('list', { name: 'Closed' }).getByRole('link')).toHaveText(['Acme']);
  await page.keyboard.press('Escape');

  await page.getByRole('button', { name: 'Reopen' }).click();

  await expect(page.getByText('This company is closed.')).toHaveCount(0);
  await expect(page.getByRole('region', { name: 'Company' }).getByLabel('Company name')).toBeEnabled();
});

test('members of a closed company see it, but not the way back', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [closed(asMember)] });
  await page.goto(`/c/${ACME.company.id}`);

  await expect(page.getByText('This company is closed.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Reopen' })).toHaveCount(0);
});

test('/ opens a company in use rather than a closed one', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [closed(ACME), GLOBEX] });

  await page.goto('/');

  await expect(page).toHaveURL(`/c/${GLOBEX.company.id}`);
});
