import { ApiError } from '#lib/api/client.ts';
import { m } from '#lib/paraglide/messages.js';

/**
 * The message to show for a failed API call, in the user's language. Picked by the problem's `code`; the
 * backend's own `detail` is English and meant for developers, so it is never shown.
 */
export function errorMessage(error: unknown): string {
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
