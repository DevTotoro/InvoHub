import { afterEach } from 'vitest';
import { getLocale, overwriteGetLocale, type Locale } from '#lib/paraglide/runtime.js';

/** Lets a test pick the language InvoHub is shown in: call `useLocale('el')`. Undone after each test. */
export function testLocale(): (locale: Locale) => void {
  const original = getLocale;
  afterEach(() => overwriteGetLocale(original));
  return (locale) => overwriteGetLocale(() => locale);
}
