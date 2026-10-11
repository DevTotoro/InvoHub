import type { ProblemDetail } from './problem.ts';

const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

export type Fetch = typeof fetch;

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  /** Request body, sent as JSON */
  json?: unknown;
  /** Pass SvelteKit's `fetch` when calling from a load function */
  fetch?: Fetch;
  /** Set to false for calls that handle a lost session themselves, so the onSessionLost handler stays quiet */
  notifySessionLost?: boolean;
}

/** Why the session ended: it expired or was ended (401), or the account was locked. */
export type SessionLossReason = 'signed_out' | 'account_locked';

let sessionLostHandler: ((reason: SessionLossReason) => void) | undefined;

/**
 * Registers what to do when any API call finds the session gone or the account locked, e.g. send the user
 * to the login page. Only one handler is active at a time. Returns a function that removes it.
 */
export function onSessionLost(handler: (reason: SessionLossReason) => void): () => void {
  sessionLostHandler = handler;
  return () => {
    if (sessionLostHandler === handler) {
      sessionLostHandler = undefined;
    }
  };
}

/** Any failed API call: an error response (with its problem detail) or, with status 0, no response at all. */
export class ApiError extends Error {
  readonly status: number;
  readonly problem?: ProblemDetail;

  constructor(status: number, problem?: ProblemDetail, message?: string, options?: ErrorOptions) {
    super(message ?? problem?.detail ?? `Request failed with status ${status}`, options);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }

  get code(): string | undefined {
    return this.problem?.code;
  }
}

/**
 * Calls the backend and returns the parsed JSON body (undefined for 204). Browser only.
 *
 * The session cookie is sent automatically; state-changing requests also carry the CSRF token, which the
 * backend issues as a readable cookie and expects back in a header.
 */
export async function request<T = unknown>(path: string, options: RequestOptions = {}): Promise<T> {
  const { json, fetch: fetchFn = globalThis.fetch, notifySessionLost = true, ...init } = options;

  const headers = new Headers(init.headers);
  if (json !== undefined) {
    headers.set('Content-Type', 'application/json');
  }
  if (!SAFE_METHODS.has((init.method ?? 'GET').toUpperCase())) {
    headers.set(CSRF_HEADER, await csrfToken(fetchFn));
  }

  let response: Response;
  try {
    response = await fetchFn(path, {
      ...init,
      headers,
      body: json === undefined ? undefined : JSON.stringify(json),
      credentials: 'same-origin'
    });
  } catch (cause) {
    throw new ApiError(0, undefined, "Can't reach InvoHub right now. Check your connection and try again.", {
      cause
    });
  }

  if (!response.ok) {
    const error = new ApiError(response.status, await readProblem(response));
    if (notifySessionLost) {
      if (error.code === 'account_locked') {
        sessionLostHandler?.('account_locked');
      } else if (error.status === 401) {
        sessionLostHandler?.('signed_out');
      }
    }
    throw error;
  }
  return (response.status === 204 ? undefined : await response.json()) as T;
}

/** The cookie is missing on the first visit and after logout (which rotates it), so fetch one then. */
async function csrfToken(fetchFn: Fetch): Promise<string> {
  let token = readCookie(CSRF_COOKIE);
  if (!token) {
    await fetchFn('/api/auth/csrf', { credentials: 'same-origin' });
    token = readCookie(CSRF_COOKIE);
  }
  if (!token) {
    throw new ApiError(0, undefined, 'Could not obtain a CSRF token.');
  }
  return token;
}

function readCookie(name: string): string | undefined {
  const prefix = `${name}=`;
  const cookie = document.cookie.split('; ').find((c) => c.startsWith(prefix));
  return cookie && decodeURIComponent(cookie.slice(prefix.length));
}

async function readProblem(response: Response): Promise<ProblemDetail | undefined> {
  if (!response.headers.get('Content-Type')?.includes('json')) {
    return undefined;
  }
  try {
    return (await response.json()) as ProblemDetail;
  } catch {
    return undefined;
  }
}
