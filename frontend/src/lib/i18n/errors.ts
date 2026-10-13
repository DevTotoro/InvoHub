import { ApiError } from '#lib/api/client.ts';
import { graphqlProblem } from '#lib/api/graphql.ts';
import { m } from '#lib/paraglide/messages.js';

/**
 * The message to show for a failed API call, in the user's language. Picked by the problem's `code` (for
 * GraphQL, the error's `extensions.code`); the backend's own `detail` or message is English and meant for
 * developers, so it is never shown.
 */
export function errorMessage(error: unknown): string {
  const graphql = graphqlProblem(error);
  if (graphql) return graphqlMessage(graphql.code, graphql.reason);
  if (!(error instanceof ApiError)) return m.error_generic();
  if (error.status === 0) return m.error_unreachable();

  switch (error.code) {
    case 'email_delivery_failed':
      return m.error_email_delivery_failed();
    case 'conflict':
      return m.error_conflict();
    case 'forbidden':
      return m.error_forbidden();
    case 'invalid_csrf_token':
      return m.error_invalid_csrf_token();
    case 'account_suspended':
      return m.error_account_suspended();
    case 'internal_error':
      return m.error_internal();
    default:
      return m.error_generic();
  }
}

function graphqlMessage(code: string, reason?: string): string {
  switch (code) {
    case 'not_found':
      return m.error_not_found();
    case 'forbidden':
      return m.error_forbidden();
    case 'company_read_only':
      return m.error_company_read_only();
    case 'company_suspended':
      return m.error_company_suspended();
    case 'company_closed':
      return m.error_company_closed();
    case 'ownership_transfer_not_found':
      return m.error_ownership_transfer_not_found();
    case 'member_not_found':
      return m.error_member_not_found();
    case 'invitation_not_found':
      return m.error_invitation_not_found();
    case 'bank_account_not_found':
      return m.error_bank_account_not_found();
    case 'email_delivery_failed':
      return m.error_email_delivery_failed();
    case 'not_allowed':
      if (reason === 'owner') return m.error_not_allowed_owner();
      if (reason === 'self') return m.error_not_allowed_self();
      if (reason === 'owner_cannot_leave') return m.error_owner_cannot_leave();
      if (reason === 'too_many_bank_accounts') return m.error_too_many_bank_accounts();
      if (reason === 'transfer_pending') return m.error_transfer_pending();
      return m.error_forbidden();
    default:
      return m.error_generic();
  }
}
