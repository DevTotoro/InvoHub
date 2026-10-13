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

  it('uploads the logo as a form, with the CSRF token, and removes it', async () => {
    const browser = fakeBrowser();
    browser.fetch.mockImplementation(async () => new Response(null, { status: 204 }));
    const file = new Blob(['png'], { type: 'image/png' });

    await uploadLogo('0ACME', file);
    await removeLogo('0ACME');

    const [url, init] = browser.fetch.mock.calls[0];
    expect([url, init?.method]).toEqual(['/api/companies/0ACME/logo', 'PUT']);
    expect((init?.body as FormData).get('file')).toBeInstanceOf(Blob);
    expect(browser.sentHeaders(0).get('X-XSRF-TOKEN')).toBe('test-token');
    // The browser sets the multipart boundary itself
    expect(browser.sentHeaders(0).get('Content-Type')).toBeNull();
    expect(browser.fetch.mock.calls[1][1]?.method).toBe('DELETE');
  });

  it("is pending while a step that can be done isn't", () => {
    expect(setupPending([{ key: 'tax_id', done: false, available: true }])).toBe(true);
    expect(setupPending([{ key: 'tax_id', done: true, available: true }])).toBe(false);
    expect(setupPending([{ key: 'mydata', done: false, available: false }])).toBe(false);
  });
});
