import { error } from '@sveltejs/kit';
import { getCountries } from '#lib/api/companies.ts';
import { m } from '#lib/paraglide/messages.js';
import type { PageLoad } from './$types';

/** For the owner and admins: the company layout loads the settings only for them */
export const load: PageLoad = async ({ fetch, parent }) => {
  const [{ settings }, countries] = await Promise.all([parent(), getCountries(fetch)]);
  if (!settings) {
    error(404, m.error_page_not_found());
  }
  return { settings, countries };
};
