import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeBrowser, jsonResponse } from '#lib/testing/fake-browser.ts';
import { GraphQLError, graphqlProblem } from './graphql.ts';
import { acceptInvitation, declineInvitation, inviteMembers, revokeInvitation } from './invitations.ts';

describe('invitations', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('invites one address to several companies at once', async () => {
    const browser = fakeBrowser();
    browser.fetch.mockResolvedValue(jsonResponse({ data: { inviteMembers: [] } }));

    await inviteMembers('maria@example.com', [
      { companyId: '0ACME', role: 'ADMIN' },
      { companyId: '0GLOBEX', role: 'VIEWER' }
    ]);

    expect(browser.sentBody()).toMatchObject({
      variables: {
        input: {
          email: 'maria@example.com',
          companies: [
            { companyId: '0ACME', role: 'ADMIN' },
            { companyId: '0GLOBEX', role: 'VIEWER' }
          ]
        }
      }
    });
  });

  it('sends which invitation to act on', async () => {
    const browser = fakeBrowser();
    browser.fetch.mockImplementation(async () =>
      jsonResponse({ data: { acceptInvitation: {}, declineInvitation: true, revokeInvitation: true } })
    );

    await acceptInvitation('0INV1');
    await declineInvitation('0INV2');
    await revokeInvitation('0ACME', '0INV3');

    expect(browser.sentBody(0)).toMatchObject({ variables: { invitationId: '0INV1' } });
    expect(browser.sentBody(1)).toMatchObject({ variables: { invitationId: '0INV2' } });
    expect(browser.sentBody(2)).toMatchObject({ variables: { companyId: '0ACME', invitationId: '0INV3' } });
  });

  it('a refusal names the company it is about', () => {
    const error = new GraphQLError([
      { message: 'refused', extensions: { code: 'invitation_refused', reason: 'already_member', companyId: '0ACME' } }
    ]);

    expect(graphqlProblem(error)).toEqual({ code: 'invitation_refused', reason: 'already_member', companyId: '0ACME' });
  });
});
