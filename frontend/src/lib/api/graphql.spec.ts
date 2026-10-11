import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeBrowser, jsonResponse } from '#lib/testing/fake-browser.ts';
import { ApiError } from './client.ts';
import { GraphQLError, graphql } from './graphql.ts';

describe('graphql', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('posts the operation and returns its data', async () => {
    const browser = fakeBrowser();
    browser.fetch.mockResolvedValue(jsonResponse({ data: { me: { id: '1' } } }));

    const data = await graphql('query Q($id: ID!) { thing(id: $id) }', { id: '1' });

    expect(data).toEqual({ me: { id: '1' } });
    expect(browser.fetch.mock.calls[0][0]).toBe('/api/graphql');
    expect(browser.sentBody()).toEqual({ query: 'query Q($id: ID!) { thing(id: $id) }', variables: { id: '1' } });
  });

  it('throws a GraphQLError when the operation reports errors', async () => {
    const browser = fakeBrowser();
    browser.fetch.mockResolvedValue(jsonResponse({ data: null, errors: [{ message: 'Boom' }] }));

    await expect(graphql('{ me { id } }')).rejects.toThrow(GraphQLError);
  });

  it('passes HTTP failures through as ApiError', async () => {
    const browser = fakeBrowser();
    browser.fetch.mockResolvedValue(jsonResponse({ status: 401 }, 401, 'application/problem+json'));

    await expect(graphql('{ me { id } }')).rejects.toThrow(ApiError);
  });
});
