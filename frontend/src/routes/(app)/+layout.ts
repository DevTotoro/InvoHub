import { error, redirect } from '@sveltejs/kit';
import { resolve } from '$app/paths';
import { getCurrentUser } from '#lib/api/auth.ts';
import { ApiError } from '#lib/api/client.ts';
import type { LayoutLoad } from './$types';

/** Everything in (app) needs a signed-in user. Without one, go to the login page and come back afterwards. */
export const load: LayoutLoad = async ({ fetch, url, untrack }) => {
  let user;
  try {
    user = await getCurrentUser(fetch);
  } catch (e) {
    // Don't treat an outage as "signed out": the login page wouldn't work either
    error(503, e instanceof ApiError ? e.message : 'Something went wrong. Please try again.');
  }

  if (!user) {
    // Untracked, so navigating between pages doesn't re-run this load (and the API call)
    const returnTo = untrack(() => url.pathname + url.search);
    redirect(307, `${resolve('/(auth)/login')}?redirectTo=${encodeURIComponent(returnTo)}`);
  }

  return { user };
};
