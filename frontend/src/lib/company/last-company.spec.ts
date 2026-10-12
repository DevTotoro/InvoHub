import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { UserCompany } from '#lib/api/companies.ts';
import { pickCompany, rememberCompany } from './last-company.ts';

const company = (id: string): UserCompany => ({
  role: 'OWNER',
  company: { id, name: id, country: 'GR', currency: 'EUR', status: 'ACTIVE', invoicingSupported: true }
});

describe('last company', () => {
  beforeEach(() => {
    const store = new Map<string, string>();
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => store.get(key) ?? null,
      setItem: (key: string, value: string) => store.set(key, value)
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('opens the company used last', () => {
    rememberCompany('b');

    expect(pickCompany([company('a'), company('b')])?.company.id).toBe('b');
  });

  it('falls back to the first company when the last one is no longer accessible', () => {
    rememberCompany('gone');

    expect(pickCompany([company('a'), company('b')])?.company.id).toBe('a');
  });

  it('has nothing to open without companies', () => {
    expect(pickCompany([])).toBeUndefined();
  });

  it('falls back to the first company when storage is unavailable', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => {
        throw new Error('SecurityError');
      },
      setItem: () => {
        throw new Error('SecurityError');
      }
    });

    rememberCompany('b');

    expect(pickCompany([company('a'), company('b')])?.company.id).toBe('a');
  });
});
