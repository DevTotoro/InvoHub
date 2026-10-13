import { getCompanyInvitations } from '#lib/api/invitations.ts';
import { getMembers } from '#lib/api/members.ts';
import { getOwnershipTransfer } from '#lib/api/ownership.ts';
import { canManageMembers } from '#lib/company/permissions.ts';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, parent, depends }) => {
  // Reloaded with invalidate('app:members') after a change
  depends('app:members');
  const { company, role } = await parent();
  // Open invitations are for the owner and admins only; an ownership offer for the owner only
  const [members, invitations, ownershipTransfer] = await Promise.all([
    getMembers(company.id, fetch),
    canManageMembers(role, company) ? getCompanyInvitations(company.id, fetch) : [],
    role === 'OWNER' && company.status !== 'SUSPENDED' ? getOwnershipTransfer(company.id, fetch) : null
  ]);
  return { members, invitations, ownershipTransfer };
};
