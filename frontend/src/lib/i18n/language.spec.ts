import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { overwriteSetLocale, setLocale } from '#lib/paraglide/runtime.js';
import { testLocale } from '#lib/testing/locale.ts';
import { apiLanguage, followUserLanguage, localeOf } from './language.ts';

describe('language', () => {
  const useLocale = testLocale();
  const originalSetLocale = setLocale;
  const destination = 'https://app.example.com/c/0ACME';
  const switchTo = vi.fn<typeof setLocale>();
  const assign = vi.fn<Location['assign']>();

  beforeEach(() => {
    switchTo.mockReset();
    overwriteSetLocale(switchTo);
    assign.mockReset();
    vi.stubGlobal('location', { assign });
  });

  afterEach(() => {
    overwriteSetLocale(originalSetLocale);
    vi.unstubAllGlobals();
  });

  it('converts between locales and the API names', () => {
    expect(apiLanguage('el')).toBe('EL');
    expect(localeOf('EL')).toBe('el');
  });

  it('names the language in use by default', () => {
    useLocale('el');

    expect(apiLanguage()).toBe('EL');
  });

  it('carries on when the user language is the one in use', async () => {
    useLocale('el');

    await followUserLanguage('EL', destination);

    expect(switchTo).not.toHaveBeenCalled();
    expect(assign).not.toHaveBeenCalled();
  });

  it('otherwise switches, then loads the page being opened and holds the navigation', async () => {
    useLocale('en');

    const followed = vi.fn();
    void followUserLanguage('EL', destination).then(followed);
    await new Promise((resolve) => setTimeout(resolve, 10));

    expect(switchTo).toHaveBeenCalledWith('el', { reload: false });
    expect(assign).toHaveBeenCalledWith(destination);
    expect(followed).not.toHaveBeenCalled();
  });
});
