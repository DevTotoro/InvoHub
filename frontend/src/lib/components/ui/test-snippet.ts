import { createRawSnippet } from 'svelte';

/** A `children` snippet rendering plain text, for component tests. */
export function text(content: string) {
  return createRawSnippet(() => ({ render: () => `<span>${content}</span>` }));
}
