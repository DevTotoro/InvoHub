import { page } from 'vitest/browser';
import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import Header from './Header.svelte';

describe('Header.svelte', () => {
  it('renders the correct title', async () => {
    render(Header);

    await expect.element(page.getByRole('heading', { level: 1 })).toHaveTextContent('InvoHub');
  });
});
