import type { PageLoad } from './$types';

export const load: PageLoad = async ({ url }) => ({ token: url.searchParams.get('token') });
