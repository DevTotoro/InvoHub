import type { Fetch } from './client.ts';
import { COMPANY_FIELDS, type Company, type CompanyRole, type UserCompany } from './companies.ts';
import { graphql } from './graphql.ts';

/** An open invitation for an email address to join a company */
export interface Invitation {
  /** Public ID */
  id: string;
  /** The address invited */
  email: string;
  role: CompanyRole;
  company: Company;
  /** The inviter's email address */
  invitedBy: string;
  /** ISO-8601 */
  expiresAt: string;
}

/** The fields every query asks for, so an Invitation always has the same shape */
export const INVITATION_FIELDS = `id email role invitedBy expiresAt company { ${COMPANY_FIELDS} }`;

/**
 * Invites an address to companies, each with a role; all or nothing.
 * @throws GraphQLError: `invalid_input`, or `invitation_refused` with a reason and the companyId it is about
 */
export async function inviteMembers(
  email: string,
  companies: { companyId: string; role: CompanyRole }[]
): Promise<Invitation[]> {
  const input = { email, companies };
  const { inviteMembers } = await graphql<{ inviteMembers: Invitation[] }, { input: typeof input }>(
    `mutation InviteMembers($input: InviteMembersInput!) { inviteMembers(input: $input) { ${INVITATION_FIELDS} } }`,
    { input }
  );
  return inviteMembers;
}

/** The company's open invitations; for its owner and admins */
export async function getCompanyInvitations(companyId: string, fetch?: Fetch): Promise<Invitation[]> {
  const { invitations } = await graphql<{ invitations: Invitation[] }, { companyId: string }>(
    `query CompanyInvitations($companyId: ID!) { invitations(companyId: $companyId) { ${INVITATION_FIELDS} } }`,
    { companyId },
    { fetch }
  );
  return invitations;
}

export async function revokeInvitation(companyId: string, invitationId: string): Promise<void> {
  await graphql<unknown, { companyId: string; invitationId: string }>(
    `
      mutation RevokeInvitation($companyId: ID!, $invitationId: ID!) {
        revokeInvitation(companyId: $companyId, invitationId: $invitationId)
      }
    `,
    { companyId, invitationId }
  );
}

/** Joins the company; returns it with the user's new role */
export async function acceptInvitation(invitationId: string): Promise<UserCompany> {
  const { acceptInvitation } = await graphql<{ acceptInvitation: UserCompany }, { invitationId: string }>(
    `mutation AcceptInvitation($invitationId: ID!) {
      acceptInvitation(invitationId: $invitationId) { role company { ${COMPANY_FIELDS} } }
    }`,
    { invitationId }
  );
  return acceptInvitation;
}

export async function declineInvitation(invitationId: string): Promise<void> {
  await graphql<unknown, { invitationId: string }>(
    'mutation DeclineInvitation($invitationId: ID!) { declineInvitation(invitationId: $invitationId) }',
    { invitationId }
  );
}
