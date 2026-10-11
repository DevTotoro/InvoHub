import { page } from 'vitest/browser';
import { describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import Button from './Button.svelte';
import { text } from './test-snippet.ts';

describe('Button.svelte', () => {
  it('is a plain button by default, so it never submits a form by accident', async () => {
    await render(Button, { children: text('Save') });

    await expect.element(page.getByRole('button', { name: 'Save' })).toHaveAttribute('type', 'button');
  });

  it('can submit a form when asked to', async () => {
    await render(Button, { type: 'submit', children: text('Save') });

    await expect.element(page.getByRole('button', { name: 'Save' })).toHaveAttribute('type', 'submit');
  });

  it('handles clicks', async () => {
    const onclick = vi.fn();
    await render(Button, { onclick, children: text('Save') });

    await page.getByRole('button', { name: 'Save' }).click();

    expect(onclick).toHaveBeenCalledOnce();
  });

  it('is disabled and marked busy while loading', async () => {
    await render(Button, { loading: true, children: text('Saving') });

    const button = page.getByRole('button', { name: 'Saving' });
    await expect.element(button).toBeDisabled();
    await expect.element(button).toHaveAttribute('aria-busy', 'true');
  });
});
