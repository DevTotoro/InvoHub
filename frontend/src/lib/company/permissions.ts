import type { Company, CompanyRole } from '#lib/api/companies.ts';
import { m } from '#lib/paraglide/messages.js';

/**
 * Whether to offer managing people (changing roles, removing). Only decides what the page shows: the backend
 * checks every request regardless (CompanyPermission.MANAGE_MEMBERS).
 */
export function canManageMembers(role: CompanyRole, company: Company): boolean {
  return (role === 'OWNER' || role === 'ADMIN') && company.status === 'ACTIVE';
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
