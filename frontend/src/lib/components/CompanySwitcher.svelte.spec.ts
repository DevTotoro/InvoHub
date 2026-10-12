import { page } from 'vitest/browser';
import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import type { UserCompany } from '#lib/api/companies.ts';
import CompanySwitcher from './CompanySwitcher.svelte';

const acme: UserCompany = {
  role: 'OWNER',
  company: { id: '0ACME', name: 'Acme', country: 'GR', currency: 'EUR', status: 'ACTIVE', invoicingSupported: true }
};
const globex: UserCompany = {
  role: 'VIEWER',
  company: {
    id: '0GLOBEX',
    name: 'Globex',
    country: 'DE',
    currency: 'EUR',
    status: 'ACTIVE',
    invoicingSupported: false
  }
};

describe('CompanySwitcher.svelte', () => {
  it('shows the current company and stays closed until clicked', async () => {
    await render(CompanySwitcher, { current: acme.company, companies: [acme, globex] });

    const toggle = page.getByRole('button', { name: 'Acme' });
    await expect.element(toggle).toHaveAttribute('aria-expanded', 'false');
    await expect.element(page.getByRole('link', { name: 'Globex' })).not.toBeInTheDocument();
  });

  it('lists every company, marks the current one, and offers a new one', async () => {
    await render(CompanySwitcher, { current: acme.company, companies: [acme, globex] });

    await page.getByRole('button', { name: 'Acme' }).click();

    await expect.element(page.getByRole('button', { name: 'Acme' })).toHaveAttribute('aria-expanded', 'true');
    await expect.element(page.getByRole('link', { name: 'Acme' })).toHaveAttribute('aria-current', 'page');
    await expect.element(page.getByRole('link', { name: 'Globex' })).toHaveAttribute('href', '/c/0GLOBEX');
    await expect.element(page.getByRole('link', { name: 'New company' })).toHaveAttribute('href', '/onboarding');
  });
});
