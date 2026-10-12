// English for now; these follow the user's language once the app is translated (roadmap: Translations)
const LOCALE = 'en';

const regions = new Intl.DisplayNames([LOCALE], { type: 'region' });
const currencies = new Intl.DisplayNames([LOCALE], { type: 'currency' });

/** e.g. GR → Greece */
export function countryName(code: string): string {
  return regions.of(code) ?? code;
}

/** e.g. EUR → Euro (EUR) */
export function currencyName(code: string): string {
  return `${currencies.of(code) ?? code} (${code})`;
}
