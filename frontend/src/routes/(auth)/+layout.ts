import { redirect } from '@sveltejs/kit';
import { getCurrentUser } from '#lib/api/auth.ts';
import { safeRedirect } from '#lib/auth/redirect.ts';
import type { LayoutLoad } from './$types';

/**
 * Pages here are for signing in, so someone already signed in is sent on: where the page was asked to lead
 * (`redirectTo`), or home. A sign-in link isn't used then, so it never swaps the account. If the check itself
 * fails, the page still shows.
 */
export const load: LayoutLoad = async ({ fetch, url }) => {
  const redirectTo = safeRedirect(url.searchParams.get('redirectTo'));
  const user = await getCurrentUser(fetch).catch(() => null);
  if (user) {
    redirect(307, redirectTo);
  }
  return { redirectTo };
};
