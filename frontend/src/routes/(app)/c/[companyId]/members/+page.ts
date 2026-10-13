import { getCompanyInvitations } from '#lib/api/invitations.ts';
import { getMembers } from '#lib/api/members.ts';
import { canManageMembers } from '#lib/company/permissions.ts';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, parent, depends }) => {
  // Reloaded with invalidate('app:members') after a change
  depends('app:members');
  const { company, role } = await parent();
  // Open invitations are for the owner and admins only
  const [members, invitations] = await Promise.all([
    getMembers(company.id, fetch),
    canManageMembers(role, company) ? getCompanyInvitations(company.id, fetch) : []
  ]);
  return { members, invitations };
};
