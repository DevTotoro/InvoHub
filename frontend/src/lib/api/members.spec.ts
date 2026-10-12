import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeBrowser, jsonResponse } from '#lib/testing/fake-browser.ts';
import { changeMemberRole, getMembers, leaveCompany, removeMember } from './members.ts';

describe('members', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("lists a company's people", async () => {
    const browser = fakeBrowser();
    const owner = { userId: '0OWNER', email: 'jane@example.com', role: 'OWNER', since: '2026-01-01T00:00:00Z' };
    browser.fetch.mockResolvedValue(jsonResponse({ data: { members: [owner] } }));

    await expect(getMembers('0ACME')).resolves.toEqual([owner]);
    expect(browser.sentBody()).toMatchObject({ variables: { companyId: '0ACME' } });
  });

  it('sends the company, the person and the role', async () => {
    const browser = fakeBrowser();
    // A fresh response per call: a body can only be read once
    browser.fetch.mockImplementation(async () =>
      jsonResponse({ data: { changeMemberRole: {}, removeMember: true, leaveCompany: true } })
    );

    await changeMemberRole('0ACME', '0JOHN', 'VIEWER');
    await removeMember('0ACME', '0JOHN');
    await leaveCompany('0ACME');

    expect(browser.sentBody(0)).toMatchObject({ variables: { companyId: '0ACME', userId: '0JOHN', role: 'VIEWER' } });
    expect(browser.sentBody(1)).toMatchObject({ variables: { companyId: '0ACME', userId: '0JOHN' } });
    expect(browser.sentBody(2)).toMatchObject({ variables: { companyId: '0ACME' } });
  });
});
