<script lang="ts">
  import Button from '#lib/components/ui/Button.svelte';
  import { m } from '#lib/paraglide/messages.js';
  import { pending, settle } from './confirm.svelte.ts';

  let dialog: HTMLDialogElement | undefined = $state();
  const messageId = $props.id();

  // The browser's own modal: focus stays inside, Escape cancels, the page behind is inert
  $effect(() => {
    if (!dialog) return;
    if (pending.current && !dialog.open) dialog.showModal();
    if (!pending.current && dialog.open) dialog.close();
  });
</script>

<dialog
  bind:this={dialog}
  aria-labelledby={messageId}
  oncancel={(e) => {
    e.preventDefault();
    settle(false);
  }}
  class="m-auto w-[calc(100%-2rem)] max-w-sm border border-gray-200 bg-white p-6 shadow-lg backdrop:bg-gray-900/40"
>
  {#if pending.current}
    <p id={messageId} class="text-sm text-gray-900">{pending.current.message}</p>
    <div class="mt-6 flex justify-end gap-2">
      <!-- Focused first, so pressing Enter by reflex doesn't confirm -->
      <Button variant="secondary" autofocus onclick={() => settle(false)}>{m.confirm_cancel()}</Button>
      <Button variant={pending.current.destructive ? 'danger' : 'primary'} onclick={() => settle(true)}>
        {pending.current.confirmLabel}
      </Button>
    </div>
  {/if}
</dialog>
