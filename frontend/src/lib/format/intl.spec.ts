import { describe, expect, it } from 'vitest';
import { testLocale } from '#lib/testing/locale.ts';
import { formatAmount, formatDate, formatNumber } from './intl.ts';
import { countryName, currencyName } from './names.ts';

describe('formatting', () => {
  const useLocale = testLocale();

  it('writes values the English way', () => {
    useLocale('en');

    expect(formatNumber(1234.5)).toBe('1,234.5');
    expect(formatAmount(1234.5, 'EUR')).toBe('€1,234.50');
    expect(formatDate('2026-10-09T12:00:00Z', 'long')).toBe('October 9, 2026');
    expect(countryName('GR')).toBe('Greece');
    expect(currencyName('EUR')).toBe('Euro (EUR)');
  });

  it('writes values the Greek way', () => {
    useLocale('el');

    expect(formatNumber(1234.5)).toBe('1.234,5');
    // Intl puts a non-breaking space before the symbol
    expect(formatAmount(1234.5, 'EUR')).toBe('1.234,50\u00a0€');
    expect(formatDate('2026-10-09T12:00:00Z', 'long')).toBe('9 Οκτωβρίου 2026');
    expect(countryName('GR')).toBe('Ελλάδα');
    expect(currencyName('EUR')).toBe('Ευρώ (EUR)');
  });
});
