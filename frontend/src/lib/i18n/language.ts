import type { Language } from '#lib/api/auth.ts';
import { getLocale, setLocale, type Locale } from '#lib/paraglide/runtime.js';

/** The languages InvoHub is available in, each named in itself */
export const LANGUAGES: { locale: Locale; name: string }[] = [
  { locale: 'en', name: 'English' },
  { locale: 'el', name: 'Ελληνικά' }
];

/** The API names languages in capitals: `el` → `EL` */
export function apiLanguage(locale: Locale = getLocale()): Language {
  return locale.toUpperCase() as Language;
}

export function localeOf(language: Language): Locale {
  return language.toLowerCase() as Locale;
}

/** Shows InvoHub in [locale] from now on, remembered in a cookie. Reloads the page, so every text follows. */
export function switchLanguage(locale: Locale): void {
  setLocale(locale);
}

/**
 * Makes sure a signed-in user sees InvoHub in their own language, which wins over one picked while signed
 * out. If it differs from the current one, this switches and loads [destination] (the page being opened)
 * afresh in it. Not a reload: during a client-side navigation, e.g. right after signing in, the page on
 * screen is still the previous one. Never resolves then, so the navigation is held until the page loads
 * rather than briefly showing the wrong language.
 */
export async function followUserLanguage(language: Language, destination: string): Promise<void> {
  const locale = localeOf(language);
  if (locale === getLocale()) return;
  setLocale(locale, { reload: false });
  location.assign(destination);
  await new Promise(() => {});
}
