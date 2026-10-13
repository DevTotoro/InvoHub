import { page } from 'vitest/browser';
import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import ConfirmDialog from './ConfirmDialog.svelte';
import { confirmAction } from './confirm.svelte.ts';

describe('ConfirmDialog.svelte', () => {
  it('asks, and resolves true when confirmed', async () => {
    await render(ConfirmDialog);

    const answer = confirmAction('Remove maria@example.com from Acme?', 'Remove');
    const dialog = page.getByRole('dialog', { name: 'Remove maria@example.com from Acme?' });
    await expect.element(dialog).toBeVisible();
    await dialog.getByRole('button', { name: 'Remove' }).click();

    expect(await answer).toBe(true);
    await expect.element(dialog).not.toBeInTheDocument();
  });

  it('resolves false when cancelled, with Cancel focused first', async () => {
    await render(ConfirmDialog);

    const answer = confirmAction('Leave Acme?', 'Leave company');
    const cancel = page.getByRole('dialog').getByRole('button', { name: 'Cancel' });
    await expect.element(cancel).toHaveFocus();
    await cancel.click();

    expect(await answer).toBe(false);
  });
});
