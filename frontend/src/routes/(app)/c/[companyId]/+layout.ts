import { error } from '@sveltejs/kit';
import { getCompanySettings } from '#lib/api/settings.ts';
import { rememberCompany } from '#lib/company/last-company.ts';
import { canSeeSettings } from '#lib/company/permissions.ts';
import { m } from '#lib/paraglide/messages.js';
import type { LayoutLoad } from './$types';

/**
 * The company in the URL must be one of the user's. The URL only says which company to show; the backend
 * checks access on every request regardless.
 */
export const load: LayoutLoad = async ({ fetch, parent, params, depends }) => {
  // Reloaded with invalidate('app:company-settings') after a change
  depends('app:company-settings');
  const { user } = await parent();
  const access = user.companies.find((c) => c.company.id === params.companyId);
  if (!access) {
    error(404, m.company_not_found());
  }

  rememberCompany(access.company.id);
  // The owner and admins get the settings here: the Settings page, its tab and the Overview checklist all use them
  const settings =
    canSeeSettings(access.role) && access.company.status !== 'SUSPENDED'
      ? await getCompanySettings(access.company.id, fetch)
      : undefined;
  return { company: access.company, role: access.role, settings };
};
