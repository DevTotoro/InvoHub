import { getLocale } from '#lib/paraglide/runtime.js';

/** Dates, numbers and amounts, written the way the user's language writes them. */

/** e.g. 9 Oct 2026, or 9 Οκτ 2026 in Greek */
export function formatDate(date: Date | string, style: 'short' | 'medium' | 'long' = 'medium'): string {
  return new Intl.DateTimeFormat(getLocale(), { dateStyle: style }).format(new Date(date));
}

/** e.g. 1,234.5, or 1.234,5 in Greek */
export function formatNumber(value: number): string {
  return new Intl.NumberFormat(getLocale()).format(value);
}

/** e.g. €1,234.50, or 1.234,50 € in Greek */
export function formatAmount(amount: number, currency: string): string {
  return new Intl.NumberFormat(getLocale(), { style: 'currency', currency }).format(amount);
}
