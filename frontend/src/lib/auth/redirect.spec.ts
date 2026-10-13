import { describe, expect, it } from 'vitest';
import { safeRedirect } from './redirect.ts';

describe('safeRedirect', () => {
  it.each(['/', '/invoices', '/invoices/42?tab=items#notes'])('keeps the same-site path %s', (target) => {
    expect(safeRedirect(target)).toBe(target);
  });

  it.each([
    ['missing', null],
    ['empty', ''],
    ['absolute URL', 'https://evil.example'],
    ['protocol-relative URL', '//evil.example'],
    ['backslash trick', '/\\evil.example'],
    ['tab trick', '/\t/evil.example'],
    ['javascript URL', 'javascript:alert(1)'],
    ['relative path', 'invoices']
  ])('falls back for a %s', (_, target) => {
    expect(safeRedirect(target)).toBe('/');
  });

  it('uses the given fallback', () => {
    expect(safeRedirect('//evil.example', '/home')).toBe('/home');
  });
});
