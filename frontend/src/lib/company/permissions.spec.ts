import { describe, expect, it } from 'vitest';
import type { Company } from '#lib/api/companies.ts';
import { canManageMembers, canSeeSettings, roleName } from './permissions.ts';

const company = (status: Company['status']): Company => ({
  id: '0ACME',
  name: 'Acme',
  country: 'GR',
  currency: 'EUR',
  status,
  invoicingSupported: true
});

describe('permissions', () => {
  it('offers managing people to owners and admins of an active company', () => {
    expect(canManageMembers('OWNER', company('ACTIVE'))).toBe(true);
    expect(canManageMembers('ADMIN', company('ACTIVE'))).toBe(true);
    expect(canManageMembers('MEMBER', company('ACTIVE'))).toBe(false);
    expect(canManageMembers('VIEWER', company('ACTIVE'))).toBe(false);
  });

  it('not while the company is read-only', () => {
    expect(canManageMembers('OWNER', company('READ_ONLY'))).toBe(false);
  });

  it('shows settings to owners and admins', () => {
    expect(canSeeSettings('OWNER')).toBe(true);
    expect(canSeeSettings('ADMIN')).toBe(true);
    expect(canSeeSettings('MEMBER')).toBe(false);
    expect(canSeeSettings('VIEWER')).toBe(false);
  });

  it('names every role', () => {
    expect(roleName('OWNER')).toBe('Owner');
    expect(roleName('VIEWER')).toBe('Viewer');
  });
});
