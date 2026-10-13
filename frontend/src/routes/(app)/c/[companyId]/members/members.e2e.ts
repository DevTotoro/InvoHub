import { expect, test, type Page } from '@playwright/test';
import { ACME, GLOBEX, TEST_USER, invitation, mockApi, person } from '#lib/testing/mock-api.ts';

const john = person('john@example.com', 'ADMIN');
const maria = person('maria@example.com', 'VIEWER');
const asViewer = { ...ACME, role: 'VIEWER' as const };
const ownedGlobex = { ...GLOBEX, role: 'OWNER' as const };
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
    expect(dialog.message()).toBe('Remove maria@example.com from Acme?');
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

test('the owner invites someone to this company and another, and sees it open', async ({ page, baseURL }) => {
  const api = await mockApi(page, baseURL!, { user: TEST_USER, companies: [ACME, ownedGlobex] });
  await page.goto(`/c/${ACME.company.id}/members`);
  const form = page.getByRole('form', { name: 'Invite someone' });

  await form.getByLabel('Email').fill('maria@example.com');
  // This company is ticked already
  await expect(form.getByRole('checkbox', { name: 'Acme' })).toBeChecked();
  await form.getByRole('combobox', { name: 'Role in Acme' }).selectOption('ADMIN');
  await form.getByRole('checkbox', { name: 'Globex' }).check();
  await form.getByRole('button', { name: 'Send invitation' }).click();

  await expect(form.getByRole('status')).toHaveText('Invitation sent to maria@example.com.');
  expect(api.invites()).toEqual([
    {
      email: 'maria@example.com',
      companies: [
        { companyId: ACME.company.id, role: 'ADMIN' },
        { companyId: GLOBEX.company.id, role: 'MEMBER' }
      ]
    }
  ]);
  await expect(page.getByRole('list', { name: 'Open invitations' }).getByRole('listitem')).toHaveText([
    /maria@example.com.*Admin · invited by owner@example.com/s
  ]);
});

test('an open invitation can be revoked', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, {
    user: TEST_USER,
    companies: [ACME],
    companyInvitations: { [ACME.company.id]: [invitation(ACME.company, 'maria@example.com')] }
  });
  await page.goto(`/c/${ACME.company.id}/members`);

  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: 'Revoke the invitation for maria@example.com' }).click();

  await expect(page.getByRole('list', { name: 'Open invitations' })).toHaveCount(0);
});

test('a refused invitation says why, and for which company', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [ACME] });
  await page.goto(`/c/${ACME.company.id}/members`);
  await page.route('**/api/graphql', (route) =>
    route.request().postData()?.includes('InviteMembers')
      ? route.fulfill({
          json: {
            data: null,
            errors: [
              {
                message: 'x',
                extensions: { code: 'invitation_refused', reason: 'already_member', companyId: ACME.company.id }
              }
            ]
          }
        })
      : route.fallback()
  );
  const form = page.getByRole('form', { name: 'Invite someone' });

  await form.getByLabel('Email').fill('maria@example.com');
  await form.getByRole('button', { name: 'Send invitation' }).click();

  await expect(form.getByRole('alert')).toHaveText('maria@example.com is already in Acme.');
});

test('a viewer has no invite form and sees no open invitations', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, companies: [asViewer] });

  await page.goto(`/c/${ACME.company.id}/members`);

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Team');
  await expect(page.getByRole('form', { name: 'Invite someone' })).toHaveCount(0);
});
