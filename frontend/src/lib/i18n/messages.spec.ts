import { describe, expect, it } from 'vitest';
import el from '../../../messages/el.json';
import en from '../../../messages/en.json';

const translations: Record<string, Record<string, string>> = { el };
// Which parameters a text uses; a translation may use one more or fewer times than English
const parameters = (text: string) => [...new Set([...text.matchAll(/\{(\w+)\}/g)].map((match) => match[1]))].sort();

describe('messages', () => {
  for (const [locale, messages] of Object.entries(translations)) {
    it(`${locale} has exactly the English texts`, () => {
      expect(Object.keys(messages).sort()).toEqual(Object.keys(en).sort());
    });

    it(`${locale} uses the same parameters as English`, () => {
      for (const [key, text] of Object.entries(en)) {
        expect(parameters(messages[key] ?? ''), key).toEqual(parameters(text));
      }
    });
  }
});
