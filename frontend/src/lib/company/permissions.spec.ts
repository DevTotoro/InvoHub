import { describe, expect, it } from 'vitest';
import type { Company } from '#lib/api/companies.ts';
import { canChange, canEdit, canManageMembers, canSeeSettings, roleName } from './permissions.ts';

const company = (status: Company['status']): Company => ({
  id: '0ACME',
  name: 'Acme',
  country: 'GR',
  currency: 'EUR',
  status,
  invoicingSupported: true,
  closedAt: null
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

  it('nor once the owner has closed it', () => {
    expect(canManageMembers('OWNER', { ...company('ACTIVE'), closedAt: '2026-10-13T10:00:00Z' })).toBe(false);
    expect(canChange({ ...company('ACTIVE'), closedAt: '2026-10-13T10:00:00Z' })).toBe(false);
    expect(canChange(company('ACTIVE'))).toBe(true);
  });

  it('shows settings to owners and admins', () => {
    expect(canSeeSettings('OWNER')).toBe(true);
    expect(canSeeSettings('ADMIN')).toBe(true);
    expect(canSeeSettings('MEMBER')).toBe(false);
    expect(canSeeSettings('VIEWER')).toBe(false);
  });

  it("lets everyone but viewers edit the company's work, while it can be changed", () => {
    expect(canEdit('MEMBER', company('ACTIVE'))).toBe(true);
    expect(canEdit('VIEWER', company('ACTIVE'))).toBe(false);
    expect(canEdit('OWNER', company('READ_ONLY'))).toBe(false);
  });

  it('names every role', () => {
    expect(roleName('OWNER')).toBe('Owner');
    expect(roleName('VIEWER')).toBe('Viewer');
  });
});
