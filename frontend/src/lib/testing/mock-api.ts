import type { Page, Route } from '@playwright/test';
import type { User } from '#lib/api/auth.ts';
import type { Company, Country, UserCompany } from '#lib/api/companies.ts';

type Identity = Omit<User, 'companies'>;

export const TEST_USER: Identity = { id: '0RWNQHYRRA7BF', email: 'jane@example.com' };

export const ACME: UserCompany = {
  role: 'OWNER',
  company: { id: '0ACMEGREECE01', name: 'Acme', country: 'GR', currency: 'EUR', invoicingSupported: true }
};

export const GLOBEX: UserCompany = {
  role: 'ADMIN',
  company: { id: '0GLOBEXGMBH01', name: 'Globex', country: 'DE', currency: 'EUR', invoicingSupported: false }
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
}

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
  { user = null, companies = [], verify = 'ok' }: MockApiOptions = {}
) {
  let currentUser = user;
  const userCompanies = [...companies];
  let created = 0;

  // The client reads the CSRF token from this cookie instead of calling /api/auth/csrf
  await page.context().addCookies([{ name: 'XSRF-TOKEN', value: 'test-token', url: baseURL }]);

  await page.route('**/api/graphql', (route) => {
    if (!currentUser) return route.fulfill(problem(401, 'Sign in to continue.'));
    const { query, variables } = route.request().postDataJSON() as {
      query: string;
      variables?: { input?: { name: string; country: string } };
    };

    if (query.includes('CreateCompany')) return createCompany(route, variables!.input!);
    if (query.includes('Countries')) return route.fulfill({ json: { data: { countries: COUNTRIES } } });
    return route.fulfill({ json: { data: { me: { ...currentUser, companies: userCompanies } } } });
  });

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
      invoicingSupported: country.invoicingSupported
    };
    userCompanies.push({ role: 'OWNER', company });
    return route.fulfill({ json: { data: { createCompany: company } } });
  }

  await page.route('**/api/auth/request', (route) =>
    route.fulfill({ json: { message: 'Check your inbox for a sign-in link.' } })
  );
  await page.route('**/api/auth/verify', (route) => {
    if (verify === 'invalid') return route.fulfill(problem(401, 'This sign-in link is invalid or has expired.'));
    if (verify === 'suspended') return route.fulfill(problem(403, 'This account is suspended.', 'account_suspended'));
    currentUser = TEST_USER;
    return route.fulfill({ status: 204 });
  });
  await page.route('**/api/auth/logout', (route) => {
    currentUser = null;
    return route.fulfill({ status: 204 });
  });

  return {
    /** Suspends the signed-in user, as InvoHub staff would: like the backend, this ends their session */
    suspend: () => {
      currentUser = null;
    }
  };
}
