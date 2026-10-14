import { error } from '@sveltejs/kit';
import { getCountries } from '#lib/api/companies.ts';
import { getContact } from '#lib/api/contacts.ts';
import { graphqlProblem } from '#lib/api/graphql.ts';
import { m } from '#lib/paraglide/messages.js';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, parent, params, depends }) => {
  depends('app:contact');
  const { company } = await parent();
  try {
    const [contact, countries] = await Promise.all([
      getContact(company.id, params.contactId, fetch),
      getCountries(fetch)
    ]);
    return { contact, countries };
  } catch (e) {
    if (graphqlProblem(e)?.code === 'contact_not_found') error(404, m.error_page_not_found());
    throw e;
  }
};
