import { ApiError, request, type Fetch } from './client.ts';
import { COMPANY_FIELDS, type UserCompany } from './companies.ts';
import { graphql } from './graphql.ts';
import { INVITATION_FIELDS, type Invitation } from './invitations.ts';

/** A language InvoHub is available in, as the API names it */
export type Language = 'EN' | 'EL';

export interface User {
  id: string;
  email: string;
  /** For the app and the user's emails */
  language: Language;
  /** The companies the user owns or is a member of, by name */
  companies: UserCompany[];
  /** Open invitations for the user's email address, oldest first */
  invitations: Invitation[];
}

/**
 * Emails a sign-in link. [language] is the one in use: the email is in it, unless the person already has an
 * account (then it's in theirs). [redirectTo] is where the link leads once signed in.
 */
export async function requestMagicLink(email: string, language: Language, redirectTo?: string): Promise<void> {
  await request('/api/auth/request', { method: 'POST', json: { email, language, redirectTo } });
}

/**
 * Exchanges the token from a magic link for a session. Signing in for the first time creates the account,
 * in [language].
 * @throws ApiError 401 with code `invalid_link` if the link is unknown, expired or used; 403 with code
 * `account_suspended` if the user is suspended
 */
export async function verifyMagicLink(token: string, language: Language): Promise<void> {
  await request('/api/auth/verify', { method: 'POST', json: { token, language } });
}

/** Saves the language the signed-in user sees InvoHub and gets emails in */
export async function setLanguage(language: Language): Promise<void> {
  await graphql<unknown, { language: Language }>(
    'mutation SetLanguage($language: Language!) { setLanguage(language: $language) { language } }',
    { language }
  );
}

export async function logout(): Promise<void> {
  await request('/api/auth/logout', { method: 'POST' });
}

/** The signed-in user, or null without a session. Other failures (e.g. API unreachable) are thrown. */
export async function getCurrentUser(fetch?: Fetch): Promise<User | null> {
  try {
    // Callers decide what a missing session means, so don't trigger the global handler
    const { me } = await graphql<{ me: User | null }>(
      `query CurrentUser {
        me { id email language companies { role company { ${COMPANY_FIELDS} } } invitations { ${INVITATION_FIELDS} } }
      }`,
      undefined,
      { fetch, notifySessionLost: false }
    );
    return me;
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) {
      return null;
    }
    throw e;
  }
}
