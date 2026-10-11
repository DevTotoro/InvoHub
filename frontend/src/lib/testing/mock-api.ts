import type { Page } from '@playwright/test';
import type { User } from '#lib/api/auth.ts';

export const TEST_USER: User = { id: '0RWNQHYRRA7BF', email: 'jane@example.com' };

export interface MockApiOptions {
  /** Signed in from the start */
  user?: User | null;
  /** How POST /api/auth/verify answers */
  verify?: 'ok' | 'invalid' | 'locked';
}

const problem = (status: number, detail: string, code?: string) => ({
  status,
  contentType: 'application/problem+json',
  body: JSON.stringify({ status, detail, code })
});

/**
 * Stands in for the backend in end-to-end tests by answering /api calls inside the browser. It keeps a
 * session like the real API: verifying a link signs TEST_USER in, logging out signs them out.
 */
export async function mockApi(page: Page, baseURL: string, { user = null, verify = 'ok' }: MockApiOptions = {}) {
  let currentUser = user;

  // The client reads the CSRF token from this cookie instead of calling /api/auth/csrf
  await page.context().addCookies([{ name: 'XSRF-TOKEN', value: 'test-token', url: baseURL }]);

  await page.route('**/api/graphql', (route) =>
    currentUser
      ? route.fulfill({ json: { data: { me: currentUser } } })
      : route.fulfill(problem(401, 'Sign in to continue.'))
  );
  await page.route('**/api/auth/request', (route) =>
    route.fulfill({ json: { message: 'Check your inbox for a sign-in link.' } })
  );
  await page.route('**/api/auth/verify', (route) => {
    if (verify === 'invalid') return route.fulfill(problem(401, 'This sign-in link is invalid or has expired.'));
    if (verify === 'locked') return route.fulfill(problem(403, 'This account is locked.', 'account_locked'));
    currentUser = TEST_USER;
    return route.fulfill({ status: 204 });
  });
  await page.route('**/api/auth/logout', (route) => {
    currentUser = null;
    return route.fulfill({ status: 204 });
  });

  return {
    /** Locks the signed-in user's account, as an admin would: like the backend, this ends their session */
    lock: () => {
      currentUser = null;
    }
  };
}
