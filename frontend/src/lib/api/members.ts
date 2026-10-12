import type { Fetch } from './client.ts';
import type { CompanyRole } from './companies.ts';
import { graphql } from './graphql.ts';

/** Someone with access to a company: its owner or a member */
export interface Member {
  /** The user's public ID */
  userId: string;
  email: string;
  role: CompanyRole;
  /** When they got access, ISO-8601 */
  since: string;
}

/** The roles that can be given to someone; ownership only moves through a transfer */
export const ASSIGNABLE_ROLES: CompanyRole[] = ['ADMIN', 'MEMBER', 'VIEWER'];

const MEMBER_FIELDS = 'userId email role since';

/** The owner first, then the members by email */
export async function getMembers(companyId: string, fetch?: Fetch): Promise<Member[]> {
  const { members } = await graphql<{ members: Member[] }, { companyId: string }>(
    `query Members($companyId: ID!) { members(companyId: $companyId) { ${MEMBER_FIELDS} } }`,
    { companyId },
    { fetch }
  );
  return members;
}

/** @throws GraphQLError, see the schema for the codes */
export async function changeMemberRole(companyId: string, userId: string, role: CompanyRole): Promise<Member> {
  const { changeMemberRole } = await graphql<
    { changeMemberRole: Member },
    { companyId: string; userId: string; role: CompanyRole }
  >(
    `mutation ChangeMemberRole($companyId: ID!, $userId: ID!, $role: CompanyRole!) {
      changeMemberRole(companyId: $companyId, userId: $userId, role: $role) { ${MEMBER_FIELDS} }
    }`,
    { companyId, userId, role }
  );
  return changeMemberRole;
}

export async function removeMember(companyId: string, userId: string): Promise<void> {
  await graphql<unknown, { companyId: string; userId: string }>(
    'mutation RemoveMember($companyId: ID!, $userId: ID!) { removeMember(companyId: $companyId, userId: $userId) }',
    { companyId, userId }
  );
}

export async function leaveCompany(companyId: string): Promise<void> {
  await graphql<unknown, { companyId: string }>(
    'mutation LeaveCompany($companyId: ID!) { leaveCompany(companyId: $companyId) }',
    { companyId }
  );
}
