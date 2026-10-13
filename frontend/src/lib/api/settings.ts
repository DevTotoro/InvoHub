import { ApiError, type Fetch } from './client.ts';
import { COMPANY_FIELDS, type Company } from './companies.ts';
import { graphql } from './graphql.ts';

/** One item of the invoicing setup checklist */
export interface SetupStep {
  /** e.g. tax_id, business_details, mydata */
  key: string;
  done: boolean;
  /** False for steps that can't be done in InvoHub yet */
  available: boolean;
}

export interface BankAccount {
  id: string;
  bankName: string;
  iban: string;
}

/** One of the country's own details, e.g. Greece's tax office (taxOffice) */
export interface CountryDetail {
  key: string;
  value: string | null;
}

/** What the owner and admins can change about a company */
export interface CompanySettings {
  company: Company;
  legalName: string | null;
  address: { street: string | null; city: string | null; postalCode: string | null };
  taxId: string | null;
  /** Every detail the country has, in form order */
  countryDetails: CountryDetail[];
  bankAccounts: BankAccount[];
  /** Where the logo is served, or null without one */
  logoUrl: string | null;
  invoicingSetup: SetupStep[];
}

export interface BusinessDetails {
  legalName: string;
  street: string;
  city: string;
  postalCode: string;
  taxId: string;
  countryDetails: { key: string; value: string }[];
}

/** Steps the company can still do something about */
export function setupPending(steps: SetupStep[]): boolean {
  return steps.some((s) => s.available && !s.done);
}

const SETTINGS_FIELDS = `
  company { ${COMPANY_FIELDS} }
  legalName
  address { street city postalCode }
  taxId
  countryDetails { key value }
  bankAccounts { id bankName iban }
  logoUrl
  invoicingSetup { key done available }
`;

/** For the owner and admins only; anyone else gets `forbidden` */
export async function getCompanySettings(companyId: string, fetch?: Fetch): Promise<CompanySettings> {
  const { companySettings } = await graphql<{ companySettings: CompanySettings }, { companyId: string }>(
    `query CompanySettings($companyId: ID!) { companySettings(companyId: $companyId) { ${SETTINGS_FIELDS} } }`,
    { companyId },
    { fetch }
  );
  return companySettings;
}

/**
 * Moving to another country clears the tax ID and the country's own details.
 * @throws GraphQLError with `invalid_input` for a bad name or country
 */
export async function updateCompanyProfile(
  companyId: string,
  input: { name: string; country: string }
): Promise<CompanySettings> {
  const { updateCompanyProfile } = await graphql<
    { updateCompanyProfile: CompanySettings },
    { companyId: string; input: typeof input }
  >(
    `mutation UpdateCompanyProfile($companyId: ID!, $input: CompanyProfileInput!) {
      updateCompanyProfile(companyId: $companyId, input: $input) { ${SETTINGS_FIELDS} }
    }`,
    { companyId, input }
  );
  return updateCompanyProfile;
}

/** Blank clears a detail. @throws GraphQLError with `invalid_input` naming the field */
export async function updateBusinessDetails(companyId: string, input: BusinessDetails): Promise<CompanySettings> {
  const { updateBusinessDetails } = await graphql<
    { updateBusinessDetails: CompanySettings },
    { companyId: string; input: BusinessDetails }
  >(
    `mutation UpdateBusinessDetails($companyId: ID!, $input: BusinessDetailsInput!) {
      updateBusinessDetails(companyId: $companyId, input: $input) { ${SETTINGS_FIELDS} }
    }`,
    { companyId, input }
  );
  return updateBusinessDetails;
}

/** @throws GraphQLError with `invalid_input` (bankName, iban) or `not_allowed` (too_many_bank_accounts) */
export async function addBankAccount(
  companyId: string,
  input: { bankName: string; iban: string }
): Promise<CompanySettings> {
  const { addBankAccount } = await graphql<
    { addBankAccount: CompanySettings },
    { companyId: string; input: typeof input }
  >(
    `mutation AddBankAccount($companyId: ID!, $input: BankAccountInput!) {
      addBankAccount(companyId: $companyId, input: $input) { ${SETTINGS_FIELDS} }
    }`,
    { companyId, input }
  );
  return addBankAccount;
}

export async function removeBankAccount(companyId: string, bankAccountId: string): Promise<CompanySettings> {
  const { removeBankAccount } = await graphql<
    { removeBankAccount: CompanySettings },
    { companyId: string; bankAccountId: string }
  >(
    `mutation RemoveBankAccount($companyId: ID!, $bankAccountId: ID!) {
      removeBankAccount(companyId: $companyId, bankAccountId: $bankAccountId) { ${SETTINGS_FIELDS} }
    }`,
    { companyId, bankAccountId }
  );
  return removeBankAccount;
}

/** Where the browser uploads a logo itself, from prepareLogoUpload */
interface LogoUpload {
  uploadId: string;
  url: string;
  headers: { name: string; value: string }[];
}

/**
 * Changes the logo: the file goes straight to storage, never through InvoHub, and is checked there before it
 * becomes the logo. PNG, JPEG or WebP, up to 1 MB.
 * @throws GraphQLError with `invalid_input`, field `logo` and the reason; ApiError if the upload itself fails
 */
export async function uploadLogo(companyId: string, file: Blob): Promise<CompanySettings> {
  const { prepareLogoUpload: upload } = await graphql<
    { prepareLogoUpload: LogoUpload },
    { companyId: string; input: { contentType: string; size: number } }
  >(
    `
      mutation PrepareLogoUpload($companyId: ID!, $input: LogoUploadInput!) {
        prepareLogoUpload(companyId: $companyId, input: $input) {
          uploadId
          url
          headers {
            name
            value
          }
        }
      }
    `,
    { companyId, input: { contentType: file.type, size: file.size } }
  );

  // Not through request(): this goes to storage, which must get neither the session cookie nor the CSRF token
  let response: Response;
  try {
    response = await fetch(upload.url, {
      method: 'PUT',
      headers: Object.fromEntries(upload.headers.map((h) => [h.name, h.value])),
      body: file,
      credentials: 'omit'
    });
  } catch (cause) {
    throw new ApiError(0, undefined, undefined, { cause });
  }
  if (!response.ok) throw new ApiError(response.status);

  const { changeLogo } = await graphql<{ changeLogo: CompanySettings }, { companyId: string; uploadId: string }>(
    `mutation ChangeLogo($companyId: ID!, $uploadId: ID!) {
      changeLogo(companyId: $companyId, uploadId: $uploadId) { ${SETTINGS_FIELDS} }
    }`,
    { companyId, uploadId: upload.uploadId }
  );
  return changeLogo;
}

export async function removeLogo(companyId: string): Promise<CompanySettings> {
  const { removeLogo } = await graphql<{ removeLogo: CompanySettings }, { companyId: string }>(
    `mutation RemoveLogo($companyId: ID!) { removeLogo(companyId: $companyId) { ${SETTINGS_FIELDS} } }`,
    { companyId }
  );
  return removeLogo;
}
