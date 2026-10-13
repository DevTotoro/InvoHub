import { describe, expect, it } from 'vitest';
import { ApiError } from '#lib/api/client.ts';
import { GraphQLError } from '#lib/api/graphql.ts';
import { testLocale } from '#lib/testing/locale.ts';
import { errorMessage } from './errors.ts';

const problem = (status: number, code?: string) =>
  new ApiError(status, { status, code, detail: 'English detail for developers' });

describe('errorMessage', () => {
  const useLocale = testLocale();

  it('picks the message by the problem code', () => {
    expect(errorMessage(problem(503, 'email_delivery_failed'))).toBe("The email couldn't be sent. Try again.");
    expect(errorMessage(problem(409, 'conflict'))).toBe('This was changed by someone else. Reload and try again.');
  });

  it('never shows the backend detail, even for an unknown code', () => {
    expect(errorMessage(problem(418, 'something_new'))).toBe('Something went wrong. Please try again.');
  });

  it('says when the API is unreachable', () => {
    expect(errorMessage(new ApiError(0))).toBe("Can't reach InvoHub. Check your connection.");
  });

  it('has a message for anything that is not an API error', () => {
    expect(errorMessage(new TypeError('boom'))).toBe('Something went wrong. Please try again.');
  });

  it('is in the language in use', () => {
    useLocale('el');

    expect(errorMessage(problem(500, 'internal_error'))).toBe('Κάτι πήγε στραβά από την πλευρά μας. Δοκιμάστε ξανά.');
  });

  it('reads GraphQL errors by their code and reason', () => {
    const graphqlError = (code: string, reason?: string) =>
      new GraphQLError([{ message: 'English, for developers', extensions: { code, reason } }]);

    expect(errorMessage(graphqlError('not_found'))).toBe("This no longer exists, or you don't have access to it.");
    expect(errorMessage(graphqlError('not_allowed', 'self'))).toBe("You can't change your own access.");
    expect(errorMessage(graphqlError('not_allowed', 'owner_cannot_leave'))).toBe("The owner can't leave the company.");
    expect(errorMessage(graphqlError('not_allowed', 'too_many_bank_accounts'))).toBe(
      'You can add up to 10 bank accounts.'
    );
    expect(errorMessage(graphqlError('bank_account_not_found'))).toBe('This bank account no longer exists.');
    expect(errorMessage(graphqlError('something_new'))).toBe('Something went wrong. Please try again.');
  });
});
