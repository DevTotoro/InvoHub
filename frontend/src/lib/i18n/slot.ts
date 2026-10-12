/**
 * Stands in for a message parameter that the page renders itself, e.g. in bold. Pass it as the parameter,
 * then split the message around it with {@link aroundSlot}. The parameter can sit anywhere in the sentence,
 * so each language keeps its own word order.
 */
export const SLOT = '\u{E000}';

/** `m.welcome({ email: SLOT })` → the text before and after the email */
export function aroundSlot(message: string): [before: string, after: string] {
  const [before = '', after = ''] = message.split(SLOT);
  return [before, after];
}
