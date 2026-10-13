import { page } from 'vitest/browser';
import { describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import type { User } from '#lib/api/auth.ts';
import UserMenu from './UserMenu.svelte';

vi.mock('#lib/api/auth.ts', () => ({ logout: vi.fn() }));
vi.mock('$app/navigation', () => ({ goto: vi.fn() }));

const user = (invitations = 0): User => ({
  id: '0JANE',
  email: 'jane@example.com',
  language: 'EN',
  companies: [],
  ownershipOffers: [],
  invitations: Array.from({ length: invitations }, (_, i) => ({
    id: `0INV${i}`,
    email: 'jane@example.com',
    role: 'MEMBER',
    invitedBy: 'owner@example.com',
    expiresAt: '2026-10-20T10:00:00Z',
    company: {
      id: `0C${i}`,
      name: `C${i}`,
      country: 'GR',
      currency: 'EUR',
      status: 'ACTIVE',
      invoicingSupported: true,
      closedAt: null
    }
  }))
});

describe('UserMenu.svelte', () => {
  it("shows the user's email, and opens to it, their invitations and sign out", async () => {
    await render(UserMenu, { user: user() });

    const button = page.getByRole('button', { name: 'Account' });
    await expect.element(button).toHaveTextContent('jane@example.com');
    await expect.element(button).toHaveAttribute('title', 'jane@example.com');
    await button.click();

    await expect.element(page.getByRole('link', { name: 'Invitations' })).toHaveAttribute('href', '/invitations');
    await expect.element(page.getByRole('button', { name: 'Sign out' })).toBeInTheDocument();
    await expect.element(page.getByRole('button', { name: 'English' })).not.toBeInTheDocument();
  });

  it('marks open invitations on the button', async () => {
    await render(UserMenu, { user: user(2) });

    await page.getByRole('button', { name: 'Account, Invitations (2)' }).click();

    await expect.element(page.getByRole('link', { name: 'Invitations (2)' })).toBeInTheDocument();
  });
});
