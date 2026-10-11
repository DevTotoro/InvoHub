import { page } from 'vitest/browser';
import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import Input from './Input.svelte';

describe('Input.svelte', () => {
  it('is labelled', async () => {
    await render(Input, { label: 'Email', type: 'email' });

    await expect.element(page.getByLabelText('Email')).toHaveAttribute('type', 'email');
  });

  it('is described by its hint', async () => {
    await render(Input, { label: 'Email', hint: 'We never share it.' });

    await expect.element(page.getByLabelText('Email')).toHaveAccessibleDescription('We never share it.');
    await expect.element(page.getByLabelText('Email')).not.toHaveAttribute('aria-invalid');
  });

  it('marks the field invalid and announces the error', async () => {
    await render(Input, { label: 'Email', hint: 'We never share it.', error: 'Enter a valid email address.' });

    const input = page.getByLabelText('Email');
    await expect.element(input).toHaveAttribute('aria-invalid', 'true');
    await expect.element(input).toHaveAccessibleDescription('Enter a valid email address.');
    await expect.element(page.getByRole('alert')).toHaveTextContent('Enter a valid email address.');
  });

  it('shows what the user types', async () => {
    await render(Input, { label: 'Email' });

    await page.getByLabelText('Email').fill('jane@example.com');

    await expect.element(page.getByLabelText('Email')).toHaveValue('jane@example.com');
  });
});
