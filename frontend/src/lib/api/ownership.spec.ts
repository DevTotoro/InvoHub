import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeBrowser, jsonResponse } from '#lib/testing/fake-browser.ts';
import {
  acceptOwnership,
  cancelOwnershipTransfer,
  closeCompany,
  declineOwnership,
  getOwnershipTransfer,
  offerOwnership,
  reopenCompany
} from './ownership.ts';

describe('ownership', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("reads the company's open offer, or none", async () => {
    const browser = fakeBrowser();
    browser.fetch.mockResolvedValue(jsonResponse({ data: { ownershipTransfer: null } }));

    await expect(getOwnershipTransfer('0ACME')).resolves.toBeNull();
    expect(browser.sentBody()).toMatchObject({ variables: { companyId: '0ACME' } });
  });

  it('sends the company and the person for each step of a transfer', async () => {
    const browser = fakeBrowser();
    // A fresh response per call: a body can only be read once
    browser.fetch.mockImplementation(async () =>
      jsonResponse({
        data: {
          offerOwnership: { to: 'maria@example.com' },
          cancelOwnershipTransfer: true,
          acceptOwnership: { role: 'OWNER' },
          declineOwnership: true
        }
      })
    );

    await expect(offerOwnership('0ACME', '0MARIA')).resolves.toEqual({ to: 'maria@example.com' });
    await cancelOwnershipTransfer('0ACME');
    await expect(acceptOwnership('0OFFER')).resolves.toEqual({ role: 'OWNER' });
    await declineOwnership('0OFFER');

    expect(browser.sentBody(0)).toMatchObject({ variables: { companyId: '0ACME', userId: '0MARIA' } });
    expect(browser.sentBody(1)).toMatchObject({ variables: { companyId: '0ACME' } });
    expect(browser.sentBody(2)).toMatchObject({ variables: { transferId: '0OFFER' } });
    expect(browser.sentBody(3)).toMatchObject({ variables: { transferId: '0OFFER' } });
  });

  it('closes and reopens a company, answering with it as it now is', async () => {
    const browser = fakeBrowser();
    browser.fetch
      .mockResolvedValueOnce(jsonResponse({ data: { closeCompany: { closedAt: '2026-10-13T10:00:00Z' } } }))
      .mockResolvedValueOnce(jsonResponse({ data: { reopenCompany: { closedAt: null } } }));

    await expect(closeCompany('0ACME')).resolves.toEqual({ closedAt: '2026-10-13T10:00:00Z' });
    await expect(reopenCompany('0ACME')).resolves.toEqual({ closedAt: null });
    expect(browser.sentBody(1)).toMatchObject({ variables: { companyId: '0ACME' } });
  });
});
