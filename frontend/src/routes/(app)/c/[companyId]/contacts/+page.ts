import { CONTACTS_PAGE_SIZE as PAGE_SIZE, getContacts } from '#lib/api/contacts.ts';
import type { PageLoad } from './$types';

/** The search, the archived view and the page are in the URL, so they survive a reload and the back button */
export const load: PageLoad = async ({ fetch, parent, url, depends }) => {
  depends('app:contacts');
  const { company } = await parent();
  const query = url.searchParams.get('q') ?? '';
  const archived = url.searchParams.get('archived') === '1';
  const page = Math.max(1, Number(url.searchParams.get('page')) || 1);
  const result = await getContacts(
    company.id,
    { query: query || undefined, archived, offset: (page - 1) * PAGE_SIZE, limit: PAGE_SIZE },
    fetch
  );
  return { ...result, query, archived, page };
};
