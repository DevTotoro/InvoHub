import { getMembers } from '#lib/api/members.ts';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, parent, depends }) => {
  // Reloaded with invalidate('app:members') after a change
  depends('app:members');
  const { company } = await parent();
  return { members: await getMembers(company.id, fetch) };
};
