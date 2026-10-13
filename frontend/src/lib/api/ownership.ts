import type { Fetch } from './client.ts';
import { COMPANY_FIELDS, type Company, type UserCompany } from './companies.ts';
import { graphql } from './graphql.ts';

/** The owner offering ownership of a company to one of its members */
export interface OwnershipTransfer {
  id: string;
  company: Company;
  /** The owner's email */
  from: string;
  /** The recipient's email */
  to: string;
  /** ISO-8601 */
  expiresAt: string;
}

/** The fields every query asks for, so an OwnershipTransfer always has the same shape */
export const OWNERSHIP_TRANSFER_FIELDS = `id from to expiresAt company { ${COMPANY_FIELDS} }`;

/** The company's open offer, if any; owner only */
export async function getOwnershipTransfer(companyId: string, fetch?: Fetch): Promise<OwnershipTransfer | null> {
  const { ownershipTransfer } = await graphql<{ ownershipTransfer: OwnershipTransfer | null }, { companyId: string }>(
    `query OwnershipTransfer($companyId: ID!) {
      ownershipTransfer(companyId: $companyId) { ${OWNERSHIP_TRANSFER_FIELDS} }
    }`,
    { companyId },
    { fetch }
  );
  return ownershipTransfer;
}

/** Emails the member, who accepts or declines. @throws GraphQLError: `not_allowed` (self, transfer_pending) */
export async function offerOwnership(companyId: string, userId: string): Promise<OwnershipTransfer> {
  const { offerOwnership } = await graphql<
    { offerOwnership: OwnershipTransfer },
    { companyId: string; userId: string }
  >(
    `mutation OfferOwnership($companyId: ID!, $userId: ID!) {
      offerOwnership(companyId: $companyId, userId: $userId) { ${OWNERSHIP_TRANSFER_FIELDS} }
    }`,
    { companyId, userId }
  );
  return offerOwnership;
}

export async function cancelOwnershipTransfer(companyId: string): Promise<void> {
  await graphql<unknown, { companyId: string }>(
    'mutation CancelOwnershipTransfer($companyId: ID!) { cancelOwnershipTransfer(companyId: $companyId) }',
    { companyId }
  );
}

/** Makes the user the owner; returns the company with their new role */
export async function acceptOwnership(transferId: string): Promise<UserCompany> {
  const { acceptOwnership } = await graphql<{ acceptOwnership: UserCompany }, { transferId: string }>(
    `mutation AcceptOwnership($transferId: ID!) {
      acceptOwnership(transferId: $transferId) { role company { ${COMPANY_FIELDS} } }
    }`,
    { transferId }
  );
  return acceptOwnership;
}

export async function declineOwnership(transferId: string): Promise<void> {
  await graphql<unknown, { transferId: string }>(
    'mutation DeclineOwnership($transferId: ID!) { declineOwnership(transferId: $transferId) }',
    { transferId }
  );
}

/** Read-only from now on, until reopened; nothing is deleted. Owner only. */
export async function closeCompany(companyId: string): Promise<Company> {
  const { closeCompany } = await graphql<{ closeCompany: Company }, { companyId: string }>(
    `mutation CloseCompany($companyId: ID!) { closeCompany(companyId: $companyId) { ${COMPANY_FIELDS} } }`,
    { companyId }
  );
  return closeCompany;
}

export async function reopenCompany(companyId: string): Promise<Company> {
  const { reopenCompany } = await graphql<{ reopenCompany: Company }, { companyId: string }>(
    `mutation ReopenCompany($companyId: ID!) { reopenCompany(companyId: $companyId) { ${COMPANY_FIELDS} } }`,
    { companyId }
  );
  return reopenCompany;
}
