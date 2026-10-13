import { describe, expect, it } from 'vitest';
import { m } from '#lib/paraglide/messages.js';
import { SLOT, aroundSlot } from './slot.ts';

describe('aroundSlot', () => {
  it('splits a message around the parameter, wherever each language puts it', () => {
    expect(aroundSlot(m.login_sent_detail({ email: SLOT }, { locale: 'en' }))).toEqual([
      "We've sent a sign-in link to ",
      '.'
    ]);
    expect(aroundSlot(m.company_welcome({ email: SLOT }, { locale: 'el' }))).toEqual(['Καλώς ήρθατε, ', '']);
  });
});
