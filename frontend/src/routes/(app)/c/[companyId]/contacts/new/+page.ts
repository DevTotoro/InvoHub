import { error } from '@sveltejs/kit';
import { getCountries } from '#lib/api/companies.ts';
import { canEdit } from '#lib/company/permissions.ts';
import { m } from '#lib/paraglide/messages.js';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, parent }) => {
  const { company, role } = await parent();
  if (!canEdit(role, company)) {
    error(404, m.error_page_not_found());
  }
  return { countries: await getCountries(fetch) };
};
