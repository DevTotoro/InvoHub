import { request, type RequestOptions } from './client.ts';

export interface GraphQLErrorEntry {
  message: string;
  path?: (string | number)[];
}

/** The request reached the API, but the GraphQL operation reported errors. */
export class GraphQLError extends Error {
  readonly errors: GraphQLErrorEntry[];

  constructor(errors: GraphQLErrorEntry[]) {
    super(errors.map((e) => e.message).join('; '));
    this.name = 'GraphQLError';
    this.errors = errors;
  }
}

/** Runs a GraphQL operation and returns its `data`. Throws ApiError (HTTP level) or GraphQLError. */
export async function graphql<TData, TVariables extends Record<string, unknown> = Record<string, never>>(
  query: string,
  variables?: TVariables,
  options: Pick<RequestOptions, 'fetch' | 'signal'> = {}
): Promise<TData> {
  const result = await request<{ data?: TData; errors?: GraphQLErrorEntry[] }>('/api/graphql', {
    ...options,
    method: 'POST',
    json: { query, variables }
  });
  if (result.errors?.length) {
    throw new GraphQLError(result.errors);
  }
  return result.data as TData;
}
