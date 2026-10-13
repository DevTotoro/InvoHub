import type { Company, CompanyRole } from '#lib/api/companies.ts';
import { m } from '#lib/paraglide/messages.js';

/**
 * Whether to offer managing people (changing roles, removing). Only decides what the page shows: the backend
 * checks every request regardless (CompanyPermission.MANAGE_MEMBERS).
 */
export function canManageMembers(role: CompanyRole, company: Company): boolean {
  return (role === 'OWNER' || role === 'ADMIN') && canChange(company);
}

/** Whether the company can be changed at all: active (paid, not suspended) and not closed by its owner */
export function canChange(company: Company): boolean {
  return company.status === 'ACTIVE' && company.closedAt === null;
}

/**
 * Whether to show the company's settings: to the owner and admins, also while the company is read-only (they can
 * look, not change). The backend checks every request regardless (CompanyPermission.MANAGE_SETTINGS).
 */
export function canSeeSettings(role: CompanyRole): boolean {
  return role === 'OWNER' || role === 'ADMIN';
}

export function roleName(role: CompanyRole): string {
  switch (role) {
    case 'OWNER':
      return m.role_owner();
    case 'ADMIN':
      return m.role_admin();
    case 'MEMBER':
      return m.role_member();
    case 'VIEWER':
      return m.role_viewer();
  }
}
