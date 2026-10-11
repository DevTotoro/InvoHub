import { redirect } from '@sveltejs/kit';
import { getCurrentUser } from '#lib/api/auth.ts';
import { safeRedirect } from '#lib/auth/redirect.ts';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, url }) => {
  const redirectTo = safeRedirect(url.searchParams.get('redirectTo'));

  // Already signed in: carry on. If the check itself fails, still show the form
  const user = await getCurrentUser(fetch).catch(() => null);
  if (user) {
    redirect(307, redirectTo);
  }

  return { redirectTo };
};
