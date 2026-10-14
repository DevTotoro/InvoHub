import type { Page, Route } from '@playwright/test';
import type { Language, User } from '#lib/api/auth.ts';
import type { Company, Country, UserCompany } from '#lib/api/companies.ts';
import type { Invitation } from '#lib/api/invitations.ts';
import type { Contact, ContactInput } from '#lib/api/contacts.ts';
import type { Member } from '#lib/api/members.ts';
import type { OwnershipTransfer } from '#lib/api/ownership.ts';
import type { BusinessDetails, CompanySettings } from '#lib/api/settings.ts';

type Identity = Omit<User, 'companies' | 'invitations' | 'ownershipOffers'>;

export const TEST_USER: Identity = { id: '0RWNQHYRRA7BF', email: 'jane@example.com', language: 'EN' };

export const ACME: UserCompany = {
  role: 'OWNER',
  company: {
    id: '0ACMEGREECE01',
    name: 'Acme',
    country: 'GR',
    currency: 'EUR',
    status: 'ACTIVE',
    invoicingSupported: true,
    closedAt: null
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
    invoicingSupported: false,
    closedAt: null
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
  /** Ownership of companies offered to the user, who must be a member of each */
  ownershipOffers?: OwnershipTransfer[];
  /** Saved settings of the user's companies, by company ID; nothing filled in otherwise */
  settings?: Record<string, Partial<CompanySettings>>;
  /** The contacts of the user's companies, by company ID */
  contacts?: Record<string, Contact[]>;
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

/** A small PNG, to upload as a logo */
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
    ownershipOffers = [],
    settings = {},
    contacts = {}
  }: MockApiOptions = {}
) {
  const myInvitations = [...invitations];
  const myOffers = [...ownershipOffers];
  /** The open ownership offer of each of the user's companies (as its owner), by company ID */
  const offersMade: Record<string, OwnershipTransfer> = {};
  const contactsOf: Record<string, Contact[]> = Object.fromEntries(
    Object.entries(contacts).map(([id, list]) => [id, list.map((c) => ({ ...c }))])
  );
  let contactsAdded = 0;
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
  /** Files in mock storage, by key */
  const storedFiles: Record<string, Buffer> = {};
  let uploads = 0;

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
    if (
      /UpdateCompanyProfile|UpdateBusinessDetails|AddBankAccount|RemoveBankAccount|Logo|CompanySettings/.test(query)
    ) {
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
    const operation = query.match(/^\s*(?:query|mutation) (\w+)/)?.[1];
    if (operation && /^(Contacts|Contact|CreateContact|UpdateContact|ArchiveContact|RestoreContact)$/.test(operation)) {
      if (!access) return graphqlError(route, 'not_found');
      return contact(route, operation, access, variables as Record<string, unknown>);
    }
    if (/OwnershipTransfer|OfferOwnership|AcceptOwnership|DeclineOwnership|CloseCompany|ReopenCompany/.test(query)) {
      return ownership(route, query, access, variables as Record<string, string>);
    }
    if (query.includes('LeaveCompany')) {
      if (!access) return graphqlError(route, 'not_found');
      if (access.role === 'OWNER') return graphqlError(route, 'not_allowed', 'owner_cannot_leave');
      userCompanies.splice(userCompanies.indexOf(access), 1);
      return route.fulfill({ json: { data: { leaveCompany: true } } });
    }
    if (query.includes('Countries')) return route.fulfill({ json: { data: { countries: COUNTRIES } } });
    return route.fulfill({
      json: {
        data: {
          me: { ...currentUser, companies: userCompanies, invitations: myInvitations, ownershipOffers: myOffers }
        }
      }
    });
  });

  function graphqlError(route: Route, code: string, reason?: string) {
    return route.fulfill({ json: { data: null, errors: [{ message: code, extensions: { code, reason } }] } });
  }

  function ownership(route: Route, query: string, access: UserCompany | undefined, variables: Record<string, string>) {
    // Answered by the recipient, about a company they're a member of
    if (/AcceptOwnership|DeclineOwnership/.test(query)) {
      const index = myOffers.findIndex((o) => o.id === variables.transferId);
      if (index < 0) return graphqlError(route, 'ownership_transfer_not_found');
      const [offer] = myOffers.splice(index, 1);
      if (query.includes('DeclineOwnership')) return route.fulfill({ json: { data: { declineOwnership: true } } });
      const owned = userCompanies.find((c) => c.company.id === offer.company.id)!;
      owned.role = 'OWNER';
      return route.fulfill({ json: { data: { acceptOwnership: owned } } });
    }

    if (!access) return graphqlError(route, 'not_found');
    if (access.role !== 'OWNER') return graphqlError(route, 'forbidden');
    const companyId = access.company.id;
    if (query.includes('CancelOwnershipTransfer')) {
      if (!offersMade[companyId]) return graphqlError(route, 'ownership_transfer_not_found');
      delete offersMade[companyId];
      return route.fulfill({ json: { data: { cancelOwnershipTransfer: true } } });
    }
    if (query.includes('OfferOwnership')) {
      if (offersMade[companyId]) return graphqlError(route, 'not_allowed', 'transfer_pending');
      const to = others[companyId]?.find((p) => p.userId === variables.userId);
      if (!to) return graphqlError(route, 'member_not_found');
      offersMade[companyId] = {
        id: `0OFFER${companyId.slice(1, 8)}`,
        company: access.company,
        from: currentUser!.email,
        to: to.email,
        expiresAt: '2026-10-20T10:00:00Z'
      };
      return route.fulfill({ json: { data: { offerOwnership: offersMade[companyId] } } });
    }
    if (query.includes('OwnershipTransfer')) {
      return route.fulfill({ json: { data: { ownershipTransfer: offersMade[companyId] ?? null } } });
    }
    // Replaced, not changed: the company object may be the shared ACME or GLOBEX
    const closing = query.includes('CloseCompany');
    access.company = { ...access.company, closedAt: closing ? '2026-10-13T10:00:00Z' : null };
    return route.fulfill({ json: { data: { [closing ? 'closeCompany' : 'reopenCompany']: access.company } } });
  }

  function contact(route: Route, operation: string, access: UserCompany, variables: Record<string, unknown>) {
    const list = (contactsOf[access.company.id] ??= []);
    if (operation === 'Contacts') {
      const query = String(variables.query ?? '').toLowerCase();
      const offset = Number(variables.offset ?? 0);
      const limit = Number(variables.limit ?? 50);
      const matching = list
        .filter((c) => Boolean(c.archivedAt) === Boolean(variables.archived))
        .filter((c) => [c.name, c.taxId, c.email].some((v) => v?.toLowerCase().includes(query)))
        .sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()));
      return route.fulfill({
        json: { data: { contacts: { total: matching.length, contacts: matching.slice(offset, offset + limit) } } }
      });
    }

    const found = operation === 'CreateContact' ? undefined : list.find((c) => c.id === variables.contactId);
    if (operation !== 'CreateContact' && !found) return graphqlError(route, 'contact_not_found');
    if (operation === 'Contact') return route.fulfill({ json: { data: { contact: found } } });
    if (operation === 'ArchiveContact' || operation === 'RestoreContact') {
      found!.archivedAt = operation === 'ArchiveContact' ? '2026-10-14T10:00:00Z' : null;
      return route.fulfill({ json: { data: { [operation[0].toLowerCase() + operation.slice(1)]: found } } });
    }

    if (access.role === 'VIEWER') return graphqlError(route, 'forbidden');
    const input = variables.input as ContactInput;
    if (!input.name.trim()) return invalid(route, 'name', 'required');
    const country = input.country || access.company.country;
    const taxId = country === 'GR' ? input.taxId.replace(/\W/g, '').replace(/^(EL|GR)/i, '') : input.taxId.trim();
    if (taxId && country === 'GR' && !validAfm(taxId)) {
      return invalid(route, 'taxId', /^\d{9}$/.test(taxId) ? 'check_digit' : 'invalid');
    }
    const text = (value: string) => value.trim() || null;
    const saved: Contact = {
      id: found?.id ?? `0CONTACT${String(++contactsAdded).padStart(5, '0')}`,
      kind: input.kind,
      name: input.name.trim(),
      country,
      taxId: taxId || null,
      email: text(input.email),
      phone: text(input.phone),
      address: { street: text(input.street), city: text(input.city), postalCode: text(input.postalCode) },
      notes: text(input.notes),
      archivedAt: found?.archivedAt ?? null
    };
    if (found) Object.assign(found, saved);
    else list.push(saved);
    return route.fulfill({
      json: { data: { [operation === 'CreateContact' ? 'createContact' : 'updateContact']: saved } }
    });
  }

  function invalid(route: Route, field: string, reason: string) {
    return route.fulfill({
      json: { data: null, errors: [{ message: 'invalid', extensions: { code: 'invalid_input', field, reason } }] }
    });
  }

  function changeSettings(route: Route, query: string, access: UserCompany, variables: Record<string, unknown>) {
    const current = settingsOf(access);
    const operation = query.match(
      /(UpdateCompanyProfile|UpdateBusinessDetails|AddBankAccount|RemoveBankAccount|PrepareLogoUpload|ChangeLogo|RemoveLogo)/
    )?.[1];
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
      const mainActivity = input.countryDetails.find((d) => d.key === 'mainActivity')?.value.replace(/[\s.-]/g, '');
      if (mainActivity && !/^\d{2,8}$/.test(mainActivity)) {
        return invalid(route, 'mainActivity', 'invalid');
      }
      current.legalName = text(input.legalName);
      current.address = { street: text(input.street), city: text(input.city), postalCode: text(input.postalCode) };
      current.taxId = taxId || null;
      current.countryDetails = current.countryDetails.map(({ key }) => {
        const value = input.countryDetails.find((d) => d.key === key)?.value ?? '';
        return { key, value: (key === 'mainActivity' ? value.replace(/[\s.-]/g, '') : value.trim()) || null };
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
    if (operation === 'PrepareLogoUpload') {
      const { contentType, size } = variables.input as { contentType: string; size: number };
      if (!['image/png', 'image/jpeg', 'image/webp'].includes(contentType)) {
        return invalid(route, 'logo', 'unsupported_type');
      }
      if (size > 1024 * 1024) return invalid(route, 'logo', 'too_large');
      const uploadId = `upload-${++uploads}`;
      const prepareLogoUpload = {
        uploadId,
        url: `${baseURL}/mock-storage/pending/${uploadId}`,
        headers: [{ name: 'content-type', value: contentType }]
      };
      return route.fulfill({ json: { data: { prepareLogoUpload } } });
    }
    if (operation === 'ChangeLogo') {
      const pending = `pending/${variables.uploadId}`;
      const file = storedFiles[pending];
      if (!file) return invalid(route, 'logo', 'upload_missing');
      delete storedFiles[pending];
      // Only PNGs are recognized here, by their first bytes like the backend
      if (!file.subarray(0, 4).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47]))) {
        return invalid(route, 'logo', 'unsupported_type');
      }
      const key = `logos/${access.company.id}/${uploads}.png`;
      storedFiles[key] = file;
      current.logoUrl = `${baseURL}/mock-storage/${key}`;
    }
    if (operation === 'RemoveLogo') current.logoUrl = null;
    if (operation === 'RemoveBankAccount') {
      const index = current.bankAccounts.findIndex((a) => a.id === variables.bankAccountId);
      if (index < 0) return graphqlError(route, 'bank_account_not_found');
      current.bankAccounts.splice(index, 1);
    }
    return answer();
  }

  // Storage, which the browser uploads logos to and loads them from, as it would S3 and the CDN
  await page.route('**/mock-storage/**', async (route) => {
    const key = new URL(route.request().url()).pathname.replace('/mock-storage/', '');
    if (route.request().method() === 'PUT') {
      storedFiles[key] = route.request().postDataBuffer() ?? Buffer.alloc(0);
      return route.fulfill({ status: 200 });
    }
    const file = storedFiles[key];
    return file ? route.fulfill({ status: 200, contentType: 'image/png', body: file }) : route.fulfill({ status: 404 });
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
      invoicingSupported: country.invoicingSupported,
      closedAt: null
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
