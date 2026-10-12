import type { Fetch } from './client.ts';
import { GraphQLError, graphql } from './graphql.ts';

export type CompanyRole = 'OWNER' | 'ADMIN' | 'MEMBER' | 'VIEWER';

export interface Company {
  /** Public ID, as used in URLs */
  id: string;
  name: string;
  /** ISO 3166-1 alpha-2, e.g. GR */
  country: string;
  /** ISO 4217, e.g. EUR */
  currency: string;
  /** Whether InvoHub supports invoicing in the company's country yet */
  invoicingSupported: boolean;
}

/** A company the user can access, with their role in it */
export interface UserCompany {
  company: Company;
  role: CompanyRole;
}

export interface Country {
  code: string;
  currency: string;
  invoicingSupported: boolean;
}

/** The fields every query asks for, so a Company always has the same shape */
export const COMPANY_FIELDS = 'id name country currency invoicingSupported';

export async function getCountries(fetch?: Fetch): Promise<Country[]> {
  const { countries } = await graphql<{ countries: Country[] }>(
    'query Countries { countries { code currency invoicingSupported } }',
    undefined,
    { fetch }
  );
  return countries;
}

/**
 * Creates a company owned by the signed-in user; the currency follows from the country.
 * @throws GraphQLError with an `invalid_input` error for a bad name or country (see {@link invalidInput})
 */
export async function createCompany(input: { name: string; country: string }): Promise<Company> {
  const { createCompany } = await graphql<{ createCompany: Company }, { input: typeof input }>(
    `mutation CreateCompany($input: CreateCompanyInput!) { createCompany(input: $input) { ${COMPANY_FIELDS} } }`,
    { input }
  );
  return createCompany;
}

/** The field and reason of the backend refusing an input, e.g. `{ field: 'name', reason: 'required' }` */
export function invalidInput(error: unknown): { field: string; reason: string } | undefined {
  if (!(error instanceof GraphQLError)) return undefined;
  const extensions = error.errors.find((e) => e.extensions?.code === 'invalid_input')?.extensions;
  return extensions && { field: String(extensions.field), reason: String(extensions.reason) };
}
