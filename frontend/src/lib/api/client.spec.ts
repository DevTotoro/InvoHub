import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeBrowser, jsonResponse } from '#lib/testing/fake-browser.ts';
import { ApiError, onSessionLost, request } from './client.ts';

/** Awaits a request that must fail and returns its ApiError. */
async function failure(promise: Promise<unknown>): Promise<ApiError> {
  const error = await promise.then(
    () => expect.unreachable('Expected the request to fail'),
    (e: unknown) => e
  );
  expect(error).toBeInstanceOf(ApiError);
  return error as ApiError;
}

describe('request', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('returns the parsed JSON body', async () => {
    const browser = fakeBrowser();
    browser.fetch.mockResolvedValue(jsonResponse({ message: 'hi' }));

    await expect(request('/api/thing')).resolves.toEqual({ message: 'hi' });
  });

  it('returns undefined for 204 No Content', async () => {
    fakeBrowser();

    await expect(request('/api/thing', { method: 'POST' })).resolves.toBeUndefined();
  });

  it('sends a JSON body with the matching content type', async () => {
    const browser = fakeBrowser();

    await request('/api/thing', { method: 'POST', json: { a: 1 } });

    expect(browser.sentHeaders().get('Content-Type')).toBe('application/json');
    expect(browser.sentBody()).toEqual({ a: 1 });
  });

  it('does not attach a CSRF token to safe requests', async () => {
    const browser = fakeBrowser();

    await request('/api/thing');

    expect(browser.sentHeaders().has('X-XSRF-TOKEN')).toBe(false);
  });

  it('echoes the CSRF cookie in the header of state-changing requests', async () => {
    const browser = fakeBrowser({ csrfToken: 'abc%3D' });

    await request('/api/thing', { method: 'POST' });

    expect(browser.sentHeaders().get('X-XSRF-TOKEN')).toBe('abc=');
  });

  it('primes the CSRF cookie when it is missing', async () => {
    const browser = fakeBrowser({ csrfToken: null });
    browser.fetch.mockImplementationOnce(async () => {
      browser.document.cookie = 'XSRF-TOKEN=fresh';
      return new Response(null, { status: 204 });
    });

    await request('/api/auth/logout', { method: 'POST' });

    expect(browser.fetch.mock.calls[0][0]).toBe('/api/auth/csrf');
    expect(browser.sentHeaders(1).get('X-XSRF-TOKEN')).toBe('fresh');
  });

  it('uses the fetch it is given, e.g. from a SvelteKit load function', async () => {
    const browser = fakeBrowser();
    const loadFetch = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse({ ok: true }));

    await request('/api/thing', { fetch: loadFetch });

    expect(loadFetch).toHaveBeenCalledOnce();
    expect(browser.fetch).not.toHaveBeenCalled();
  });

  it('throws an ApiError carrying the problem detail', async () => {
    const browser = fakeBrowser();
    browser.fetch.mockResolvedValue(
      jsonResponse(
        { status: 403, detail: 'This account is locked.', code: 'account_locked' },
        403,
        'application/problem+json'
      )
    );

    const error = await failure(request('/api/thing'));

    expect(error.status).toBe(403);
    expect(error.code).toBe('account_locked');
    expect(error.message).toBe('This account is locked.');
  });

  it('throws an ApiError for error responses without a problem body', async () => {
    const browser = fakeBrowser();
    browser.fetch.mockResolvedValue(new Response('Bad gateway', { status: 502 }));

    const error = await failure(request('/api/thing'));

    expect(error.status).toBe(502);
    expect(error.problem).toBeUndefined();
  });

  it('throws an ApiError with status 0 when the API is unreachable', async () => {
    const browser = fakeBrowser();
    browser.fetch.mockRejectedValue(new TypeError('Failed to fetch'));

    const error = await failure(request('/api/thing'));

    expect(error.status).toBe(0);
    expect(error.message).toContain("Can't reach InvoHub");
  });
});

describe('onSessionLost', () => {
  const problem = (status: number, code?: string) => jsonResponse({ status, code }, status, 'application/problem+json');

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it.each([
    ['a 401', problem(401), 'signed_out'],
    ['a locked account', problem(403, 'account_locked'), 'account_locked']
  ])('is told about %s', async (_, response, reason) => {
    const browser = fakeBrowser();
    browser.fetch.mockResolvedValue(response);
    const handler = vi.fn();
    const stop = onSessionLost(handler);

    await request('/api/thing').catch(() => {});
    stop();

    expect(handler).toHaveBeenCalledExactlyOnceWith(reason);
  });

  it('ignores other errors', async () => {
    const browser = fakeBrowser();
    browser.fetch.mockResolvedValue(problem(403));
    const handler = vi.fn();
    const stop = onSessionLost(handler);

    await request('/api/thing').catch(() => {});
    stop();

    expect(handler).not.toHaveBeenCalled();
  });

  it('stays quiet for calls that handle a lost session themselves', async () => {
    const browser = fakeBrowser();
    browser.fetch.mockResolvedValue(problem(401));
    const handler = vi.fn();
    const stop = onSessionLost(handler);

    await request('/api/thing', { notifySessionLost: false }).catch(() => {});
    stop();

    expect(handler).not.toHaveBeenCalled();
  });

  it('stops after being removed', async () => {
    const browser = fakeBrowser();
    browser.fetch.mockResolvedValue(problem(401));
    const handler = vi.fn();
    onSessionLost(handler)();

    await request('/api/thing').catch(() => {});

    expect(handler).not.toHaveBeenCalled();
  });
});
