import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeBrowser, jsonResponse } from '#lib/testing/fake-browser.ts';
import { ApiError } from './client.ts';
import { createCompany, invalidInput } from './companies.ts';
import { GraphQLError } from './graphql.ts';

describe('companies', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('creates a company with just a name and a country', async () => {
    const browser = fakeBrowser();
    const company = {
      id: '0ACME',
      name: 'Acme',
      country: 'GR',
      currency: 'EUR',
      status: 'ACTIVE',
      invoicingSupported: true
    };
    browser.fetch.mockResolvedValue(jsonResponse({ data: { createCompany: company } }));

    await expect(createCompany({ name: 'Acme', country: 'GR' })).resolves.toEqual(company);
    expect(browser.sentBody().variables).toEqual({ input: { name: 'Acme', country: 'GR' } });
  });

  it('reads the field and reason of an invalid_input error', () => {
    const error = new GraphQLError([
      { message: 'Invalid name: required', extensions: { code: 'invalid_input', field: 'name', reason: 'required' } }
    ]);

    expect(invalidInput(error)).toEqual({ field: 'name', reason: 'required' });
  });

  it('reads them from a plain HTTP problem too', () => {
    const error = new ApiError(400, { status: 400, code: 'invalid_input', field: 'logo', reason: 'too_large' });

    expect(invalidInput(error)).toEqual({ field: 'logo', reason: 'too_large' });
  });

  it('ignores other errors', () => {
    expect(invalidInput(new GraphQLError([{ message: 'Boom' }]))).toBeUndefined();
    expect(invalidInput(new Error('Boom'))).toBeUndefined();
    expect(invalidInput(new ApiError(403, { status: 403, code: 'forbidden' }))).toBeUndefined();
  });
});
