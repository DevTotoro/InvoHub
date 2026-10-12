import { page } from 'vitest/browser';
import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import Select from './Select.svelte';

const options = [
  { value: 'GR', label: 'Greece' },
  { value: 'DE', label: 'Germany' }
];

describe('Select.svelte', () => {
  it('is labelled and offers its options', async () => {
    await render(Select, { label: 'Country', options, value: 'DE' });

    const select = page.getByLabelText('Country');
    await expect.element(select).toHaveValue('DE');
    await expect.element(page.getByRole('option', { name: 'Greece' })).toBeInTheDocument();
  });

  it('is described by its hint', async () => {
    await render(Select, { label: 'Country', options, hint: 'Currency: Euro (EUR)' });

    await expect.element(page.getByLabelText('Country')).toHaveAccessibleDescription('Currency: Euro (EUR)');
  });

  it('marks the field invalid and announces the error', async () => {
    await render(Select, { label: 'Country', options, error: 'Choose a country from the list.' });

    await expect.element(page.getByLabelText('Country')).toHaveAttribute('aria-invalid', 'true');
    await expect.element(page.getByRole('alert')).toHaveTextContent('Choose a country from the list.');
  });
});
