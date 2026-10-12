import { getLocale } from '#lib/paraglide/runtime.js';

/** e.g. GR → Greece, or Ελλάδα in Greek */
export function countryName(code: string): string {
  return new Intl.DisplayNames([getLocale()], { type: 'region' }).of(code) ?? code;
}

/** e.g. EUR → Euro (EUR), or Ευρώ (EUR) in Greek */
export function currencyName(code: string): string {
  return `${new Intl.DisplayNames([getLocale()], { type: 'currency' }).of(code) ?? code} (${code})`;
}
