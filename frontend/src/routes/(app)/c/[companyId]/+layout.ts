import { error } from '@sveltejs/kit';
import { rememberCompany } from '#lib/company/last-company.ts';
import { m } from '#lib/paraglide/messages.js';
import type { LayoutLoad } from './$types';

/**
 * The company in the URL must be one of the user's. The URL only says which company to show; the backend
 * checks access on every request regardless.
 */
export const load: LayoutLoad = async ({ parent, params }) => {
  const { user } = await parent();
  const access = user.companies.find((c) => c.company.id === params.companyId);
  if (!access) {
    error(404, m.company_not_found());
  }

  rememberCompany(access.company.id);
  return { company: access.company, role: access.role };
};
