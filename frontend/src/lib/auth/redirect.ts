const STORAGE_KEY = 'invohub:redirect-to';
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

/**
 * Remembers where to go after signing in. The magic link opens in a new tab, so this has to outlive the
 * page; on another device it is simply missing and sign-in lands on the home page.
 */
export function rememberRedirect(target: string): void {
  try {
    localStorage.setItem(STORAGE_KEY, safeRedirect(target));
  } catch {
    // Storage unavailable (e.g. blocked): fall back to the home page after sign-in
  }
}

/** Returns the remembered destination once, then forgets it. */
export function consumeRedirect(): string {
  try {
    const target = localStorage.getItem(STORAGE_KEY);
    localStorage.removeItem(STORAGE_KEY);
    return safeRedirect(target);
  } catch {
    return '/';
  }
}
