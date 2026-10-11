import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { consumeRedirect, rememberRedirect, safeRedirect } from './redirect.ts';

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

describe('remembered redirect', () => {
  beforeEach(() => {
    const store = new Map<string, string>();
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => store.get(key) ?? null,
      setItem: (key: string, value: string) => store.set(key, value),
      removeItem: (key: string) => store.delete(key)
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('is returned once', () => {
    rememberRedirect('/invoices/42');

    expect(consumeRedirect()).toBe('/invoices/42');
    expect(consumeRedirect()).toBe('/');
  });

  it('never stores an off-site target', () => {
    rememberRedirect('//evil.example');

    expect(consumeRedirect()).toBe('/');
  });

  it('falls back to the home page when storage is unavailable', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => {
        throw new Error('SecurityError');
      },
      setItem: () => {
        throw new Error('SecurityError');
      },
      removeItem: () => {}
    });

    rememberRedirect('/invoices');

    expect(consumeRedirect()).toBe('/');
  });
});
