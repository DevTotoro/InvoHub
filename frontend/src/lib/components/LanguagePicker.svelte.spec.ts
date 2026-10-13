import { page } from 'vitest/browser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { switchLanguage } from '#lib/i18n/language.ts';
import LanguagePicker from './LanguagePicker.svelte';

// Switching for real reloads the page, which would end the test run
vi.mock('#lib/i18n/language.ts', async (original) => ({
  ...(await original<typeof import('#lib/i18n/language.ts')>()),
  switchLanguage: vi.fn()
}));

describe('LanguagePicker.svelte', () => {
  beforeEach(() => {
    vi.mocked(switchLanguage).mockReset();
  });

  it('offers each language, named in itself, with the current one selected', async () => {
    await render(LanguagePicker);

    const picker = page.getByRole('combobox', { name: 'Language' });
    await expect.element(picker).toHaveValue('en');
    await expect.element(page.getByRole('option', { name: 'Ελληνικά' })).toBeInTheDocument();
  });

  it('switches this browser to the picked language', async () => {
    await render(LanguagePicker);

    await page.getByRole('combobox', { name: 'Language' }).selectOptions('el');

    await vi.waitFor(() => expect(switchLanguage).toHaveBeenCalledWith('el'));
  });
});
