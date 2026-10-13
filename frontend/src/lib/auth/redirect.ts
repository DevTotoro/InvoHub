const PLACEHOLDER_ORIGIN = 'https://invohub.invalid';

/**
 * Returns `target` if it is a path on this site, otherwise `fallback`. Redirect targets come from URLs
 * anyone can craft, so this keeps a login link from sending users to another site after they sign in.
 * Parsing (rather than prefix checks) also catches `//host`, `/\host` and tab/newline tricks.
 */
export function safeRedirect(target: string | null | undefined, fallback = '/'): string {
  if (!target?.startsWith('/')) {
    return fallback;
  }
  try {
    const url = new URL(target, PLACEHOLDER_ORIGIN);
    return url.origin === PLACEHOLDER_ORIGIN ? url.pathname + url.search + url.hash : fallback;
  } catch {
    return fallback;
  }
}
