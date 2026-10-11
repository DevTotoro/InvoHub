import { vi } from 'vitest';

/**
 * Stubs the browser globals the API client relies on (document.cookie, fetch) for unit tests running in
 * Node. Call `vi.unstubAllGlobals()` afterwards.
 */
export function fakeBrowser({ csrfToken = 'test-token' }: { csrfToken?: string | null } = {}) {
  const document = { cookie: csrfToken ? `XSRF-TOKEN=${csrfToken}` : '' };
  const fetch = vi.fn<typeof globalThis.fetch>().mockResolvedValue(new Response(null, { status: 204 }));
  vi.stubGlobal('document', document);
  vi.stubGlobal('fetch', fetch);

  return {
    document,
    fetch,
    /** Headers of the nth fetch call */
    sentHeaders: (call = 0) => new Headers(fetch.mock.calls[call][1]?.headers),
    sentBody: (call = 0) => JSON.parse(String(fetch.mock.calls[call][1]?.body))
  };
}

export function jsonResponse(body: unknown, status = 200, contentType = 'application/json'): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } });
}
