const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';
const SAFE_METHODS = ['GET', 'HEAD', 'OPTIONS'];

/**
 * Calls the backend. The session cookie is sent automatically; state-changing requests also carry the CSRF
 * token, which the backend issues as a readable cookie and expects back in a header. Browser only.
 */
export async function api(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  if (!SAFE_METHODS.includes((init.method ?? 'GET').toUpperCase())) {
    headers.set(CSRF_HEADER, await csrfToken());
  }
  return fetch(path, { ...init, headers, credentials: 'same-origin' });
}

export function postJson(path: string, body: unknown): Promise<Response> {
  return api(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
}

/** The cookie is missing on the first visit and after logout (which rotates it), so fetch one then. */
async function csrfToken(): Promise<string> {
  const token = readCookie(CSRF_COOKIE) ?? (await fetch('/api/auth/csrf'), readCookie(CSRF_COOKIE));
  if (!token) {
    throw new Error('Could not obtain a CSRF token');
  }
  return token;
}

function readCookie(name: string): string | undefined {
  const prefix = `${name}=`;
  const cookie = document.cookie.split('; ').find((c) => c.startsWith(prefix));
  return cookie && decodeURIComponent(cookie.slice(prefix.length));
}
