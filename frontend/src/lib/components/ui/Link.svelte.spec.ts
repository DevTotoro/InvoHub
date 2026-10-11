import { page } from 'vitest/browser';
import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import Link from './Link.svelte';
import { text } from './test-snippet.ts';

describe('Link.svelte', () => {
  it('opens internal links in the same tab', async () => {
    await render(Link, { href: '/login', children: text('Sign in') });

    const link = page.getByRole('link', { name: 'Sign in' });
    await expect.element(link).toHaveAttribute('href', '/login');
    await expect.element(link).not.toHaveAttribute('target');
  });

  it('opens external links in a new tab without exposing this page to them', async () => {
    await render(Link, { href: 'https://github.com', external: true, children: text('GitHub') });

    const link = page.getByRole('link', { name: 'GitHub' });
    await expect.element(link).toHaveAttribute('target', '_blank');
    await expect.element(link).toHaveAttribute('rel', 'noopener noreferrer');
  });
});
