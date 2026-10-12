import { getCountries } from '#lib/api/companies.ts';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, parent }) => {
  const [{ user }, countries] = await Promise.all([parent(), getCountries(fetch)]);
  return { countries, firstCompany: user.companies.length === 0 };
};
