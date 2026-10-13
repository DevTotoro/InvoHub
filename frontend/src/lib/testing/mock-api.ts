import type { Page, Route } from '@playwright/test';
import type { Language, User } from '#lib/api/auth.ts';
import type { Company, Country, UserCompany } from '#lib/api/companies.ts';
import type { Invitation } from '#lib/api/invitations.ts';
import type { Member } from '#lib/api/members.ts';

type Identity = Omit<User, 'companies' | 'invitations'>;

export const TEST_USER: Identity = { id: '0RWNQHYRRA7BF', email: 'jane@example.com', language: 'EN' };

export const ACME: UserCompany = {
  role: 'OWNER',
  company: {
    id: '0ACMEGREECE01',
    name: 'Acme',
    country: 'GR',
    currency: 'EUR',
    status: 'ACTIVE',
    invoicingSupported: true
  }
};

export const GLOBEX: UserCompany = {
  role: 'ADMIN',
  company: {
    id: '0GLOBEXGMBH01',
    name: 'Globex',
    country: 'DE',
    currency: 'EUR',
    status: 'ACTIVE',
    invoicingSupported: false
  }
};

const COUNTRIES: Country[] = [
  { code: 'GR', currency: 'EUR', invoicingSupported: true },
  { code: 'DE', currency: 'EUR', invoicingSupported: false },
  { code: 'US', currency: 'USD', invoicingSupported: false }
];

export interface MockApiOptions {
  /** Signed in from the start */
  user?: Identity | null;
  /** The user's companies */
  companies?: UserCompany[];
  /** How POST /api/auth/verify answers */
  verify?: 'ok' | 'invalid' | 'suspended';
  /** The language TEST_USER's account has when signing in, unless already signed in as [user] */
  accountLanguage?: Language;
  /** Other people in the user's companies, by company ID; the user is always listed with their own role */
  people?: Record<string, Member[]>;
  /** Open invitations for the user's email address */
  invitations?: Invitation[];
  /** Open invitations of the user's companies, by company ID */
  companyInvitations?: Record<string, Invitation[]>;
}

/** An invitation to [company] for [email] */
export const invitation = (company: Company, email: string, role: Invitation['role'] = 'MEMBER'): Invitation => ({
  id: `0INV${company.id.slice(1, 5)}${email.slice(0, 4).toUpperCase()}`.padEnd(13, 'X').slice(0, 13),
  email,
  role,
  company,
  invitedBy: 'owner@example.com',
  expiresAt: '2026-10-20T10:00:00Z'
});

/** Someone else in a company, for the people option */
export const person = (email: string, role: Member['role']): Member => ({
  userId: `0${email.split('@')[0].toUpperCase().padEnd(12, 'X').slice(0, 12)}`,
  email,
  role,
  since: '2026-01-15T10:00:00Z'
});

const problem = (status: number, detail: string, code?: string) => ({
  status,
  contentType: 'application/problem+json',
  body: JSON.stringify({ status, detail, code })
});

/**
 * Stands in for the backend in end-to-end tests by answering /api calls inside the browser. It keeps state
 * like the real API: verifying a link signs TEST_USER in, logging out signs them out, and created companies
 * are added to the user's list.
 */
export async function mockApi(
  page: Page,
  baseURL: string,
  {
    user = null,
    companies = [],
    verify = 'ok',
    accountLanguage = 'EN',
    people = {},
    invitations = [],
    companyInvitations = {}
  }: MockApiOptions = {}
) {
  const myInvitations = [...invitations];
  const openInvitations: Record<string, Invitation[]> = Object.fromEntries(
    Object.entries(companyInvitations).map(([id, list]) => [id, [...list]])
  );
  /** The input of every InviteMembers call */
  const invites: unknown[] = [];
  const others: Record<string, Member[]> = Object.fromEntries(
    Object.entries(people).map(([id, members]) => [id, [...members]])
  );
  let currentUser = user;
  /** The body of every call to /api/auth/*, by path */
  const authCalls: Record<string, unknown[]> = {};
  const userCompanies = [...companies];
  let created = 0;

  // The client reads the CSRF token from this cookie instead of calling /api/auth/csrf
  await page.context().addCookies([{ name: 'XSRF-TOKEN', value: 'test-token', url: baseURL }]);

  await page.route('**/api/graphql', (route) => {
    if (!currentUser) return route.fulfill(problem(401, 'Sign in to continue.'));
    const { query, variables } = route.request().postDataJSON() as {
      query: string;
      variables?: {
        input?: { name: string; country: string };
        language?: Language;
        companyId?: string;
        userId?: string;
        role?: Member['role'];
        invitationId?: string;
      };
    };
    const access = userCompanies.find((c) => c.company.id === variables?.companyId);

    if (query.includes('CreateCompany')) return createCompany(route, variables!.input!);
    if (query.includes('SetLanguage')) {
      currentUser = { ...currentUser, language: variables!.language! };
      return route.fulfill({ json: { data: { setLanguage: { language: currentUser.language } } } });
    }
    if (query.includes('InviteMembers')) {
      const input = (
        variables as unknown as { input: { email: string; companies: { companyId: string; role: Member['role'] }[] } }
      ).input;
      invites.push(input);
      const created = input.companies.map(({ companyId, role }) => {
        const company = userCompanies.find((c) => c.company.id === companyId)!.company;
        const sent = invitation(company, input.email, role);
        (openInvitations[companyId] ??= []).push(sent);
        return sent;
      });
      return route.fulfill({ json: { data: { inviteMembers: created } } });
    }
    if (query.includes('CompanyInvitations')) {
      if (!access) return graphqlError(route, 'not_found');
      return route.fulfill({ json: { data: { invitations: openInvitations[access.company.id] ?? [] } } });
    }
    if (query.includes('RevokeInvitation')) {
      const list = openInvitations[variables!.companyId!] ?? [];
      list.splice(
        list.findIndex((i) => i.id === variables!.invitationId),
        1
      );
      return route.fulfill({ json: { data: { revokeInvitation: true } } });
    }
    if (query.includes('AcceptInvitation') || query.includes('DeclineInvitation')) {
      const index = myInvitations.findIndex((i) => i.id === variables!.invitationId);
      if (index < 0) return graphqlError(route, 'invitation_not_found');
      const [taken] = myInvitations.splice(index, 1);
      if (query.includes('DeclineInvitation')) return route.fulfill({ json: { data: { declineInvitation: true } } });
      const joined = { role: taken.role, company: taken.company };
      userCompanies.push(joined);
      return route.fulfill({ json: { data: { acceptInvitation: joined } } });
    }
    if (query.includes('Members')) {
      if (!access) return graphqlError(route, 'not_found');
      const me = { userId: currentUser.id, email: currentUser.email, role: access.role, since: '2026-01-01T10:00:00Z' };
      const everyone = [me, ...(others[access.company.id] ?? [])];
      // The owner first, then by email, like the backend
      const owner = everyone.filter((p) => p.role === 'OWNER');
      const rest = everyone.filter((p) => p.role !== 'OWNER').sort((a, b) => a.email.localeCompare(b.email));
      return route.fulfill({ json: { data: { members: [...owner, ...rest] } } });
    }
    if (query.includes('ChangeMemberRole') || query.includes('RemoveMember')) {
      if (!access) return graphqlError(route, 'not_found');
      const list = others[access.company.id] ?? [];
      const target = list.find((p) => p.userId === variables!.userId);
      if (!target || target.role === 'OWNER') return graphqlError(route, 'member_not_found');
      if (query.includes('RemoveMember')) {
        list.splice(list.indexOf(target), 1);
        return route.fulfill({ json: { data: { removeMember: true } } });
      }
      target.role = variables!.role!;
      return route.fulfill({ json: { data: { changeMemberRole: target } } });
    }
    if (query.includes('LeaveCompany')) {
      if (!access) return graphqlError(route, 'not_found');
      if (access.role === 'OWNER') return graphqlError(route, 'not_allowed', 'owner_cannot_leave');
      userCompanies.splice(userCompanies.indexOf(access), 1);
      return route.fulfill({ json: { data: { leaveCompany: true } } });
    }
    if (query.includes('Countries')) return route.fulfill({ json: { data: { countries: COUNTRIES } } });
    return route.fulfill({
      json: { data: { me: { ...currentUser, companies: userCompanies, invitations: myInvitations } } }
    });
  });

  function graphqlError(route: Route, code: string, reason?: string) {
    return route.fulfill({ json: { data: null, errors: [{ message: code, extensions: { code, reason } }] } });
  }

  function createCompany(route: Route, input: { name: string; country: string }) {
    const name = input.name.trim();
    if (!name) {
      return route.fulfill({
        json: {
          data: null,
          errors: [
            {
              message: 'Invalid name: required',
              extensions: { code: 'invalid_input', field: 'name', reason: 'required' }
            }
          ]
        }
      });
    }
    const country = COUNTRIES.find((c) => c.code === input.country)!;
    const company: Company = {
      id: `0NEWCOMPANY0${++created}`,
      name,
      country: country.code,
      currency: country.currency,
      status: 'ACTIVE',
      invoicingSupported: country.invoicingSupported
    };
    userCompanies.push({ role: 'OWNER', company });
    return route.fulfill({ json: { data: { createCompany: company } } });
  }

  await page.route('**/api/auth/request', (route) =>
    route.fulfill({ json: { message: 'Check your inbox for a sign-in link.' } })
  );
  await page.route('**/api/auth/verify', (route) => {
    if (verify === 'invalid') {
      return route.fulfill(problem(401, 'This sign-in link is invalid or has expired.', 'invalid_link'));
    }
    if (verify === 'suspended') return route.fulfill(problem(403, 'This account is suspended.', 'account_suspended'));
    currentUser = { ...TEST_USER, language: accountLanguage };
    return route.fulfill({ status: 204 });
  });
  await page.route('**/api/auth/logout', (route) => {
    currentUser = null;
    return route.fulfill({ status: 204 });
  });
  // Registered last, so it runs first (Playwright tries the newest route first), then hands over
  await page.route('**/api/auth/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    (authCalls[path] ??= []).push(route.request().postDataJSON());
    return route.fallback();
  });

  return {
    /** The input of every InviteMembers call */
    invites: () => invites,
    /** The bodies sent to an /api/auth endpoint, e.g. `authCalls('/api/auth/request')` */
    authCalls: (path: string) => authCalls[path] ?? [],
    /** The signed-in user's language, as saved on their account */
    language: () => currentUser?.language,
    /** Suspends the signed-in user, as InvoHub staff would: like the backend, this ends their session */
    suspend: () => {
      currentUser = null;
    }
  };
}
