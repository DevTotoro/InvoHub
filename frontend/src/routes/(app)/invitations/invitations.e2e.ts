import { expect, test, type Page } from '@playwright/test';
import { ACME, GLOBEX, TEST_USER, invitation, mockApi } from '#lib/testing/mock-api.ts';

const list = (page: Page) => page.getByRole('list', { name: 'Invitations' }).getByRole('listitem');

test('someone invited, with no company yet, lands on their invitations and joins', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, invitations: [invitation(ACME.company, TEST_USER.email, 'ADMIN')] });

  await page.goto('/');

  await expect(page).toHaveURL('/invitations');
  await expect(list(page)).toHaveText([/Acme.*Invited by owner@example.com as Admin/s]);
  await page.getByRole('button', { name: 'Accept' }).click();

  await expect(page).toHaveURL(`/c/${ACME.company.id}`);
  await expect(page.getByRole('link', { name: /Invitations/ })).toHaveCount(0);
});

test('the user menu points to open invitations, which can be declined', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, {
    user: TEST_USER,
    companies: [ACME],
    invitations: [invitation(GLOBEX.company, TEST_USER.email)]
  });
  await page.goto(`/c/${ACME.company.id}`);

  await page.getByRole('button', { name: 'Account, Invitations (1)' }).click();
  await page.getByRole('link', { name: 'Invitations (1)' }).click();
  await page.getByRole('button', { name: 'Decline' }).click();

  await expect(page.getByText('You have no invitations right now.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Account' })).toBeVisible();
});

test('an invitation that closed meanwhile is explained', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, { user: TEST_USER, invitations: [invitation(ACME.company, TEST_USER.email)] });
  await page.goto('/invitations');
  await page.route('**/api/graphql', (route) =>
    route.request().postData()?.includes('AcceptInvitation')
      ? route.fulfill({
          json: { data: null, errors: [{ message: 'x', extensions: { code: 'invitation_not_found' } }] }
        })
      : route.fallback()
  );

  await page.getByRole('button', { name: 'Accept' }).click();

  await expect(page.getByRole('alert')).toHaveText('This invitation is no longer open.');
});

test('onboarding mentions invitations', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, {
    user: TEST_USER,
    companies: [ACME],
    invitations: [invitation(GLOBEX.company, TEST_USER.email)]
  });

  await page.goto('/onboarding');

  await expect(page.getByRole('link', { name: 'See your invitations' })).toHaveAttribute('href', '/invitations');
});

test('in Greek', async ({ page, baseURL }) => {
  await mockApi(page, baseURL!, {
    user: { ...TEST_USER, language: 'EL' },
    invitations: [invitation(ACME.company, TEST_USER.email, 'VIEWER')]
  });

  await page.goto('/invitations');

  await expect(page.getByRole('heading', { level: 2 })).toHaveText('Προσκλήσεις');
  await expect(page.getByText('Πρόσκληση από owner@example.com ως Μόνο προβολή')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Αποδοχή' })).toBeVisible();
});
