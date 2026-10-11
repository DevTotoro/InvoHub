import { ApiError, request, type Fetch } from './client.ts';
import { graphql } from './graphql.ts';

export interface User {
  id: string;
  email: string;
}

/** Emails a sign-in link. Signing in for the first time creates the account. */
export async function requestMagicLink(email: string): Promise<void> {
  await request('/api/auth/request', { method: 'POST', json: { email } });
}

/**
 * Exchanges the token from a magic link for a session.
 * @throws ApiError 401 if the link is invalid or expired, 403 with code `account_locked` if the account is locked
 */
export async function verifyMagicLink(token: string): Promise<void> {
  await request('/api/auth/verify', { method: 'POST', json: { token } });
}

export async function logout(): Promise<void> {
  await request('/api/auth/logout', { method: 'POST' });
}

/**
 * The signed-in user, or null without a session. Other failures are thrown, including ApiError with code
 * `account_locked` when the account was locked (which also ends the session).
 */
export async function getCurrentUser(fetch?: Fetch): Promise<User | null> {
  try {
    // Callers decide what a missing session means, so don't trigger the global handler
    const { me } = await graphql<{ me: User | null }>('query CurrentUser { me { id email } }', undefined, {
      fetch,
      notifySessionLost: false
    });
    return me;
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) {
      return null;
    }
    throw e;
  }
}
