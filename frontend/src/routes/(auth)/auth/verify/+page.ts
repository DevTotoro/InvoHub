import { getCurrentUser } from '#lib/api/auth.ts';
import { safeRedirect } from '#lib/auth/redirect.ts';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, url }) => {
  // Someone already signed in can use the link right away: email scanners, which the Continue button is
  // for, never have the person's session. A failed check just shows the button.
  const signedIn = (await getCurrentUser(fetch).catch(() => null)) !== null;
  return {
    token: url.searchParams.get('token'),
    redirectTo: safeRedirect(url.searchParams.get('redirectTo')),
    signedIn
  };
};
