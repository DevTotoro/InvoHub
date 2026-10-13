import { page } from 'vitest/browser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { setLanguage, type User } from '#lib/api/auth.ts';
import { switchLanguage } from '#lib/i18n/language.ts';
import UserMenu from './UserMenu.svelte';

// Switching for real reloads the page, which would end the test run
vi.mock('#lib/i18n/language.ts', async (original) => ({
  ...(await original<typeof import('#lib/i18n/language.ts')>()),
  switchLanguage: vi.fn()
}));
vi.mock('#lib/api/auth.ts', () => ({ setLanguage: vi.fn(), logout: vi.fn() }));
vi.mock('$app/navigation', () => ({ goto: vi.fn() }));

const user = (invitations = 0): User => ({
  id: '0JANE',
  email: 'jane@example.com',
  language: 'EN',
  companies: [],
  invitations: Array.from({ length: invitations }, (_, i) => ({
    id: `0INV${i}`,
    email: 'jane@example.com',
    role: 'MEMBER',
    invitedBy: 'owner@example.com',
    expiresAt: '2026-10-20T10:00:00Z',
    company: { id: `0C${i}`, name: `C${i}`, country: 'GR', currency: 'EUR', status: 'ACTIVE', invoicingSupported: true }
  }))
});

describe('UserMenu.svelte', () => {
  beforeEach(() => {
    vi.mocked(setLanguage).mockReset().mockResolvedValue();
    vi.mocked(switchLanguage).mockReset();
  });

  it("shows the user's initial, and opens to their email, invitations, language and sign out", async () => {
    await render(UserMenu, { user: user() });

    const button = page.getByRole('button', { name: 'Account' });
    await expect.element(button).toHaveTextContent('J');
    await button.click();

    await expect.element(page.getByText('jane@example.com')).toBeInTheDocument();
    await expect.element(page.getByRole('link', { name: 'Invitations' })).toHaveAttribute('href', '/invitations');
    await expect.element(page.getByRole('button', { name: 'English' })).toHaveAttribute('aria-pressed', 'true');
    await expect.element(page.getByRole('button', { name: 'Sign out' })).toBeInTheDocument();
  });

  it('marks open invitations on the button', async () => {
    await render(UserMenu, { user: user(2) });

    await page.getByRole('button', { name: 'Account, Invitations (2)' }).click();

    await expect.element(page.getByRole('link', { name: 'Invitations (2)' })).toBeInTheDocument();
  });

  it('saves a picked language on the account before switching', async () => {
    await render(UserMenu, { user: user() });
    await page.getByRole('button', { name: 'Account' }).click();

    await page.getByRole('button', { name: 'Ελληνικά' }).click();

    await vi.waitFor(() => expect(switchLanguage).toHaveBeenCalledWith('el'));
    expect(setLanguage).toHaveBeenCalledWith('EL');
  });

  it('stays in the current language if saving fails', async () => {
    vi.mocked(setLanguage).mockRejectedValue(new Error('offline'));
    await render(UserMenu, { user: user() });
    await page.getByRole('button', { name: 'Account' }).click();

    await page.getByRole('button', { name: 'Ελληνικά' }).click();

    await expect.element(page.getByRole('button', { name: 'Ελληνικά' })).toBeEnabled();
    expect(switchLanguage).not.toHaveBeenCalled();
  });
});
