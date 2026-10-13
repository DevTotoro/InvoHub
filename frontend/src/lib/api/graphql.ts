import { request, type RequestOptions } from './client.ts';

export interface GraphQLErrorEntry {
  message: string;
  path?: (string | number)[];
  /** Set by the backend for errors clients can act on, e.g. `{ code: 'invalid_input', field, reason }` */
  extensions?: Record<string, unknown>;
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

/**
 * The `code`, and `reason` and `companyId` if any, the backend gave a failed operation, e.g.
 * `{ code: 'not_allowed', reason: 'self' }`
 */
export function graphqlProblem(error: unknown): { code: string; reason?: string; companyId?: string } | undefined {
  if (!(error instanceof GraphQLError)) return undefined;
  const extensions = error.errors.find((e) => typeof e.extensions?.code === 'string')?.extensions;
  if (!extensions) return undefined;
  const optional = (value: unknown) => (value === undefined ? undefined : String(value));
  return {
    code: String(extensions.code),
    reason: optional(extensions.reason),
    companyId: optional(extensions.companyId)
  };
}

/** Runs a GraphQL operation and returns its `data`. Throws ApiError (HTTP level) or GraphQLError. */
export async function graphql<TData, TVariables extends Record<string, unknown> = Record<string, never>>(
  query: string,
  variables?: TVariables,
  options: Pick<RequestOptions, 'fetch' | 'signal' | 'notifySessionLost'> = {}
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
