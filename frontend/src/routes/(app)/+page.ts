import { redirect } from '@sveltejs/kit';
import { resolve } from '$app/paths';
import { pickCompany } from '#lib/company/last-company.ts';
import type { PageLoad } from './$types';

/** `/` has no page of its own: it opens the company the user last worked in, or onboarding if they have none. */
export const load: PageLoad = async ({ parent }) => {
  const { user } = await parent();
  const company = pickCompany(user.companies);
  if (!company) {
    redirect(307, resolve('/(app)/onboarding'));
  }
  redirect(307, resolve('/(app)/c/[companyId]', { companyId: company.company.id }));
};
