import type { Page, Route } from '@playwright/test';
import type { Language, User } from '#lib/api/auth.ts';
import type { Company, Country, UserCompany } from '#lib/api/companies.ts';
import type { Invitation } from '#lib/api/invitations.ts';
import type { Member } from '#lib/api/members.ts';
import type { BusinessDetails, CompanySettings } from '#lib/api/settings.ts';

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
  /** Saved settings of the user's companies, by company ID; nothing filled in otherwise */
  settings?: Record<string, Partial<CompanySettings>>;
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

/** A logo, as served by GET /api/companies/{id}/logo */
export const LOGO_PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
  'base64'
);

/** Greece's ΑΦΜ check digit, as the backend checks it */
const validAfm = (taxId: string) =>
  /^\d{9}$/.test(taxId) &&
  ([...taxId.slice(0, 8)].reduce((sum, digit, i) => sum + Number(digit) * 2 ** (8 - i), 0) % 11) % 10 ===
    Number(taxId[8]);

/** ISO 13616: mod 97 of the rearranged IBAN is 1 */
const validIban = (iban: string) =>
  /^[A-Z]{2}\d{2}[A-Z0-9]{11,30}$/.test(iban) &&
  BigInt(
    [...(iban.slice(4) + iban.slice(0, 4))].map((c) => (/\d/.test(c) ? c : String(c.charCodeAt(0) - 55))).join('')
  ) %
    97n ===
    1n;

/** Recomputed from the saved details, like the backend's country rules */
function invoicingSetup(settings: CompanySettings): CompanySettings['invoicingSetup'] {
  if (settings.company.country !== 'GR') return [{ key: 'invoicing_not_available', done: false, available: false }];
  const { legalName, address, countryDetails } = settings;
  return [
    { key: 'tax_id', done: settings.taxId !== null, available: true },
    {
      key: 'business_details',
      done:
        [legalName, address.street, address.city, address.postalCode].every((v) => v !== null) &&
        countryDetails.every((d) => d.value !== null),
      available: true
    },
    { key: 'mydata', done: false, available: false }
  ];
}

const countryDetailsOf = (country: string) =>
  country === 'GR'
    ? [
        { key: 'taxOffice', value: null },
        { key: 'mainActivity', value: null }
      ]
    : [];

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
    companyInvitations = {},
    settings = {}
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
  // Copies, so changing a company (renaming it) never changes the shared ACME or GLOBEX
  const userCompanies = companies.map((c) => ({ ...c }));
  let created = 0;
  const savedSettings: Record<string, CompanySettings> = {};
  let logoVersion = 0;

  /** The company's settings as saved, with the setup checklist worked out */
  function settingsOf(access: UserCompany): CompanySettings {
    const saved = (savedSettings[access.company.id] ??= {
      legalName: null,
      address: { street: null, city: null, postalCode: null },
      taxId: null,
      countryDetails: countryDetailsOf(access.company.country),
      bankAccounts: [],
      logoUrl: null,
      invoicingSetup: [],
      ...settings[access.company.id],
      company: access.company
    });
    saved.company = access.company;
    saved.invoicingSetup = invoicingSetup(saved);
    return saved;
  }

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
    if (/UpdateCompanyProfile|UpdateBusinessDetails|AddBankAccount|RemoveBankAccount|CompanySettings/.test(query)) {
      if (!access) return graphqlError(route, 'not_found');
      if (access.role !== 'OWNER' && access.role !== 'ADMIN') return graphqlError(route, 'forbidden');
      return changeSettings(route, query, access, variables as Record<string, unknown>);
    }
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

  function invalid(route: Route, field: string, reason: string) {
    return route.fulfill({
      json: { data: null, errors: [{ message: 'invalid', extensions: { code: 'invalid_input', field, reason } }] }
    });
  }

  function changeSettings(route: Route, query: string, access: UserCompany, variables: Record<string, unknown>) {
    const current = settingsOf(access);
    const operation = query.match(/(UpdateCompanyProfile|UpdateBusinessDetails|AddBankAccount|RemoveBankAccount)/)?.[1];
    const answer = () => {
      const field = operation ? operation[0].toLowerCase() + operation.slice(1) : 'companySettings';
      return route.fulfill({ json: { data: { [field]: settingsOf(access) } } });
    };

    if (operation === 'UpdateCompanyProfile') {
      const input = variables.input as { name: string; country: string };
      if (!input.name.trim()) return invalid(route, 'name', 'required');
      const country = COUNTRIES.find((c) => c.code === input.country)!;
      const moved = country.code !== access.company.country;
      access.company = {
        ...access.company,
        name: input.name.trim(),
        country: country.code,
        currency: country.currency,
        invoicingSupported: country.invoicingSupported
      };
      if (moved) {
        current.taxId = null;
        current.countryDetails = countryDetailsOf(country.code);
      }
    }
    if (operation === 'UpdateBusinessDetails') {
      const input = variables.input as BusinessDetails;
      const text = (value: string) => value.trim() || null;
      const taxId = input.taxId.replace(/\W/g, '').replace(/^(EL|GR)/i, '');
      if (taxId && access.company.country === 'GR' && !validAfm(taxId)) {
        return invalid(route, 'taxId', /^\d{9}$/.test(taxId) ? 'check_digit' : 'invalid');
      }
      const mainActivity = input.countryDetails.find((d) => d.key === 'mainActivity')?.value.replace(/\D/g, '');
      if (mainActivity && (mainActivity.length < 2 || mainActivity.length > 8)) {
        return invalid(route, 'mainActivity', 'invalid');
      }
      current.legalName = text(input.legalName);
      current.address = { street: text(input.street), city: text(input.city), postalCode: text(input.postalCode) };
      current.taxId = taxId || null;
      current.countryDetails = current.countryDetails.map(({ key }) => {
        const value = input.countryDetails.find((d) => d.key === key)?.value ?? '';
        return { key, value: (key === 'mainActivity' ? value.replace(/\D/g, '') : value.trim()) || null };
      });
    }
    if (operation === 'AddBankAccount') {
      const input = variables.input as { bankName: string; iban: string };
      const iban = input.iban.toUpperCase().replace(/[^A-Z0-9]/g, '');
      if (!input.bankName.trim()) return invalid(route, 'bankName', 'required');
      if (!validIban(iban)) return invalid(route, 'iban', 'invalid');
      if (current.bankAccounts.some((a) => a.iban === iban)) return invalid(route, 'iban', 'duplicate');
      current.bankAccounts.push({ id: `0BANK${iban.slice(-8)}`, bankName: input.bankName.trim(), iban });
    }
    if (operation === 'RemoveBankAccount') {
      const index = current.bankAccounts.findIndex((a) => a.id === variables.bankAccountId);
      if (index < 0) return graphqlError(route, 'bank_account_not_found');
      current.bankAccounts.splice(index, 1);
    }
    return answer();
  }

  await page.route('**/api/companies/*/logo*', async (route) => {
    const companyId = new URL(route.request().url()).pathname.split('/')[3];
    const access = userCompanies.find((c) => c.company.id === companyId);
    if (!currentUser || !access) return route.fulfill(problem(404, 'Company not found.', 'not_found'));
    const current = settingsOf(access);
    const method = route.request().method();
    if (method === 'GET') {
      return current.logoUrl
        ? route.fulfill({ status: 200, contentType: 'image/png', body: LOGO_PNG })
        : route.fulfill({ status: 404 });
    }
    if (method === 'PUT') {
      const body = route.request().postDataBuffer() ?? Buffer.alloc(0);
      // Only PNGs are recognized here, by their first bytes like the backend
      if (!body.includes(Buffer.from([0x89, 0x50, 0x4e, 0x47]))) {
        return route.fulfill({
          status: 400,
          contentType: 'application/problem+json',
          body: JSON.stringify({ status: 400, code: 'invalid_input', field: 'logo', reason: 'unsupported_type' })
        });
      }
      current.logoUrl = `/api/companies/${companyId}/logo?v=${++logoVersion}`;
    } else {
      current.logoUrl = null;
    }
    return route.fulfill({ status: 204 });
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
