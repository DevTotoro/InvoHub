import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeBrowser, jsonResponse } from '#lib/testing/fake-browser.ts';
import { getCurrentUser, requestMagicLink, setLanguage, verifyMagicLink } from './auth.ts';
import { ApiError, onSessionLost } from './client.ts';

describe('auth', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('requests a magic link for the email, in the language in use', async () => {
    const browser = fakeBrowser();
    browser.fetch.mockResolvedValue(jsonResponse({ message: 'Check your inbox for a sign-in link.' }));

    await requestMagicLink('jane@example.com', 'EL');

    expect(browser.fetch.mock.calls[0][0]).toBe('/api/auth/request');
    expect(browser.sentBody()).toEqual({ email: 'jane@example.com', language: 'EL' });
  });

  it('verifies a magic link, in the language in use', async () => {
    const browser = fakeBrowser();
    browser.fetch.mockResolvedValue(new Response(null, { status: 204 }));

    await verifyMagicLink('abc', 'EL');

    expect(browser.fetch.mock.calls[0][0]).toBe('/api/auth/verify');
    expect(browser.sentBody()).toEqual({ token: 'abc', language: 'EL' });
  });

  it("saves the user's language", async () => {
    const browser = fakeBrowser();
    browser.fetch.mockResolvedValue(jsonResponse({ data: { setLanguage: { language: 'EL' } } }));

    await setLanguage('EL');

    expect(browser.sentBody()).toMatchObject({ variables: { language: 'EL' } });
  });

  it('returns the signed-in user', async () => {
    const browser = fakeBrowser();
    browser.fetch.mockResolvedValue(jsonResponse({ data: { me: { id: '0ABC', email: 'jane@example.com' } } }));

    await expect(getCurrentUser()).resolves.toEqual({ id: '0ABC', email: 'jane@example.com' });
  });

  it('returns null without a session', async () => {
    const browser = fakeBrowser();
    browser.fetch.mockResolvedValue(jsonResponse({ status: 401 }, 401, 'application/problem+json'));

    await expect(getCurrentUser()).resolves.toBeNull();
  });

  it('does not alert the global session handler, since callers decide what signed out means', async () => {
    const browser = fakeBrowser();
    browser.fetch.mockResolvedValue(jsonResponse({ status: 401 }, 401, 'application/problem+json'));
    const handler = vi.fn();
    const stop = onSessionLost(handler);

    await getCurrentUser();
    stop();

    expect(handler).not.toHaveBeenCalled();
  });

  it('throws when the API is unreachable, rather than pretending the user is signed out', async () => {
    const browser = fakeBrowser();
    browser.fetch.mockRejectedValue(new TypeError('Failed to fetch'));

    await expect(getCurrentUser()).rejects.toThrow(ApiError);
  });
});
