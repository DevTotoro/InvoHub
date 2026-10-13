import { page } from 'vitest/browser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { setLanguage } from '#lib/api/auth.ts';
import { switchLanguage } from '#lib/i18n/language.ts';
import LanguagePicker from './LanguagePicker.svelte';

// Switching for real reloads the page, which would end the test run
vi.mock('#lib/i18n/language.ts', async (original) => ({
  ...(await original<typeof import('#lib/i18n/language.ts')>()),
  switchLanguage: vi.fn()
}));
vi.mock('#lib/api/auth.ts', () => ({ setLanguage: vi.fn() }));

describe('LanguagePicker.svelte', () => {
  beforeEach(() => {
    vi.mocked(setLanguage).mockReset().mockResolvedValue();
    vi.mocked(switchLanguage).mockReset();
  });

  it('offers each language, named in itself, with the current one selected', async () => {
    await render(LanguagePicker, { signedIn: false });

    const picker = page.getByRole('combobox', { name: 'Language' });
    await expect.element(picker).toHaveValue('en');
    await expect.element(page.getByRole('option', { name: 'Ελληνικά' })).toBeInTheDocument();
  });

  it('signed out, switches this browser only', async () => {
    await render(LanguagePicker, { signedIn: false });

    await page.getByRole('combobox', { name: 'Language' }).selectOptions('el');

    await vi.waitFor(() => expect(switchLanguage).toHaveBeenCalledWith('el'));
    expect(setLanguage).not.toHaveBeenCalled();
  });

  it('signed in, saves the language on the account before switching', async () => {
    await render(LanguagePicker, { signedIn: true });

    await page.getByRole('combobox', { name: 'Language' }).selectOptions('el');

    await vi.waitFor(() => expect(switchLanguage).toHaveBeenCalledWith('el'));
    expect(setLanguage).toHaveBeenCalledWith('EL');
  });

  it('stays in the current language if saving fails', async () => {
    vi.mocked(setLanguage).mockRejectedValue(new Error('offline'));
    await render(LanguagePicker, { signedIn: true });

    const picker = page.getByRole('combobox', { name: 'Language' });
    await picker.selectOptions('el');

    await expect.element(picker).toHaveValue('en');
    await expect.element(picker).toBeEnabled();
    expect(switchLanguage).not.toHaveBeenCalled();
  });
});
