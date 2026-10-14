import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeBrowser, jsonResponse } from '#lib/testing/fake-browser.ts';
import {
  archiveContact,
  createContact,
  getContact,
  getContacts,
  restoreContact,
  updateContact,
  type ContactInput
} from './contacts.ts';

const input: ContactInput = {
  kind: 'BUSINESS',
  name: 'Papadopoulos ΑΕ',
  country: 'GR',
  taxId: '094014201',
  email: '',
  phone: '',
  street: '',
  city: '',
  postalCode: '',
  notes: ''
};

describe('contacts', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('searches one page at a time', async () => {
    const browser = fakeBrowser();
    browser.fetch.mockResolvedValue(jsonResponse({ data: { contacts: { total: 0, contacts: [] } } }));

    await expect(getContacts('0ACME', { query: 'papa', offset: 25, limit: 25 })).resolves.toEqual({
      total: 0,
      contacts: []
    });
    expect(browser.sentBody()).toMatchObject({
      variables: { companyId: '0ACME', query: 'papa', offset: 25, limit: 25 }
    });
  });

  it('sends the company, the contact and the input of each change', async () => {
    const browser = fakeBrowser();
    // A fresh response per call: a body can only be read once
    browser.fetch.mockImplementation(async () =>
      jsonResponse({
        data: { contact: {}, createContact: {}, updateContact: {}, archiveContact: {}, restoreContact: {} }
      })
    );

    await getContact('0ACME', '0C1');
    await createContact('0ACME', input);
    await updateContact('0ACME', '0C1', input);
    await archiveContact('0ACME', '0C1');
    await restoreContact('0ACME', '0C1');

    expect(browser.sentBody(0)).toMatchObject({ variables: { companyId: '0ACME', contactId: '0C1' } });
    expect(browser.sentBody(1)).toMatchObject({ variables: { companyId: '0ACME', input } });
    expect(browser.sentBody(2)).toMatchObject({ variables: { contactId: '0C1', input } });
    expect(browser.sentBody(3)).toMatchObject({ variables: { contactId: '0C1' } });
    expect(browser.sentBody(4)).toMatchObject({ variables: { contactId: '0C1' } });
  });
});
