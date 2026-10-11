import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, postJson } from './api';

describe('api', () => {
  const fetchMock = vi.fn<typeof fetch>();
  const doc = { cookie: '' };

  beforeEach(() => {
    doc.cookie = '';
    fetchMock.mockReset().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('document', doc);
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  const sentHeaders = (call: number) => new Headers(fetchMock.mock.calls[call][1]?.headers);

  it('does not attach a CSRF token to safe requests', async () => {
    await api('/api/something');

    expect(fetchMock).toHaveBeenCalledOnce();
    expect(sentHeaders(0).has('X-XSRF-TOKEN')).toBe(false);
  });

  it('echoes the CSRF cookie in the header of state-changing requests', async () => {
    doc.cookie = 'other=1; XSRF-TOKEN=abc%3D';

    await postJson('/api/auth/request', { email: 'jane@example.com' });

    expect(fetchMock).toHaveBeenCalledOnce();
    expect(sentHeaders(0).get('X-XSRF-TOKEN')).toBe('abc=');
    expect(sentHeaders(0).get('Content-Type')).toBe('application/json');
  });

  it('primes the CSRF cookie when it is missing', async () => {
    fetchMock.mockImplementationOnce(async () => {
      doc.cookie = 'XSRF-TOKEN=fresh';
      return new Response(null, { status: 204 });
    });

    await api('/api/auth/logout', { method: 'POST' });

    expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/csrf');
    expect(sentHeaders(1).get('X-XSRF-TOKEN')).toBe('fresh');
  });

  it('fails when no CSRF token can be obtained', async () => {
    await expect(api('/api/auth/logout', { method: 'POST' })).rejects.toThrow('CSRF');
  });
});
