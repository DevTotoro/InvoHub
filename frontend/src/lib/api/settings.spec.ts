import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeBrowser, jsonResponse } from '#lib/testing/fake-browser.ts';
import {
  addBankAccount,
  getCompanySettings,
  removeBankAccount,
  removeLogo,
  setupPending,
  updateBusinessDetails,
  updateCompanyProfile,
  uploadLogo
} from './settings.ts';

describe('settings', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('sends the company and the input of each change', async () => {
    const browser = fakeBrowser();
    browser.fetch.mockImplementation(async () =>
      jsonResponse({
        data: {
          companySettings: {},
          updateCompanyProfile: {},
          updateBusinessDetails: {},
          addBankAccount: {},
          removeBankAccount: {}
        }
      })
    );
    const details = {
      legalName: 'Acme ΙΚΕ',
      street: '',
      city: '',
      postalCode: '',
      taxId: '094014201',
      countryDetails: [{ key: 'taxOffice', value: 'Α Αθηνών' }]
    };

    await getCompanySettings('0ACME');
    await updateCompanyProfile('0ACME', { name: 'Acme', country: 'GR' });
    await updateBusinessDetails('0ACME', details);
    await addBankAccount('0ACME', { bankName: 'Eurobank', iban: 'GR16' });
    await removeBankAccount('0ACME', '0BANK');

    expect(browser.sentBody(0)).toMatchObject({ variables: { companyId: '0ACME' } });
    expect(browser.sentBody(1)).toMatchObject({ variables: { input: { name: 'Acme', country: 'GR' } } });
    expect(browser.sentBody(2)).toMatchObject({ variables: { input: details } });
    expect(browser.sentBody(3)).toMatchObject({ variables: { input: { bankName: 'Eurobank', iban: 'GR16' } } });
    expect(browser.sentBody(4)).toMatchObject({ variables: { companyId: '0ACME', bankAccountId: '0BANK' } });
  });

  it('uploads the logo straight to storage, then makes it the logo', async () => {
    const browser = fakeBrowser();
    const storageUrl = 'https://storage.example/pending/0ACME/u1?X-Amz-Signature=abc';
    browser.fetch
      .mockResolvedValueOnce(
        jsonResponse({
          data: {
            prepareLogoUpload: {
              uploadId: 'u1',
              url: storageUrl,
              headers: [{ name: 'content-type', value: 'image/png' }]
            }
          }
        })
      )
      .mockResolvedValueOnce(new Response(null, { status: 200 }))
      .mockResolvedValueOnce(jsonResponse({ data: { changeLogo: { logoUrl: 'https://cdn.example/logos/a.png' } } }));
    const file = new Blob(['png'], { type: 'image/png' });

    await expect(uploadLogo('0ACME', file)).resolves.toEqual({ logoUrl: 'https://cdn.example/logos/a.png' });

    expect(browser.sentBody(0)).toMatchObject({ variables: { input: { contentType: 'image/png', size: 3 } } });
    const [url, init] = browser.fetch.mock.calls[1];
    expect([url, init?.method, init?.body, init?.credentials]).toEqual([storageUrl, 'PUT', file, 'omit']);
    // Storage gets the signed headers, and never InvoHub's CSRF token
    expect(browser.sentHeaders(1).get('content-type')).toBe('image/png');
    expect(browser.sentHeaders(1).get('X-XSRF-TOKEN')).toBeNull();
    expect(browser.sentBody(2)).toMatchObject({ variables: { companyId: '0ACME', uploadId: 'u1' } });
  });

  it("doesn't make it the logo when the upload fails", async () => {
    const browser = fakeBrowser();
    browser.fetch
      .mockResolvedValueOnce(
        jsonResponse({ data: { prepareLogoUpload: { uploadId: 'u1', url: 'https://s', headers: [] } } })
      )
      .mockResolvedValueOnce(new Response(null, { status: 403 }));

    await expect(uploadLogo('0ACME', new Blob(['png']))).rejects.toMatchObject({ name: 'ApiError', status: 403 });
    expect(browser.fetch).toHaveBeenCalledTimes(2);
  });

  it('removes the logo', async () => {
    const browser = fakeBrowser();
    browser.fetch.mockResolvedValue(jsonResponse({ data: { removeLogo: {} } }));

    await removeLogo('0ACME');

    expect(browser.sentBody()).toMatchObject({ variables: { companyId: '0ACME' } });
  });

  it("is pending while a step that can be done isn't", () => {
    expect(setupPending([{ key: 'tax_id', done: false, available: true }])).toBe(true);
    expect(setupPending([{ key: 'tax_id', done: true, available: true }])).toBe(false);
    expect(setupPending([{ key: 'mydata', done: false, available: false }])).toBe(false);
  });
});
