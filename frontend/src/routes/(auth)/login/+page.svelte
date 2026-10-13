<script lang="ts">
  import { requestMagicLink } from '#lib/api/auth.ts';
  import { ApiError } from '#lib/api/client.ts';
  import Button from '#lib/components/ui/Button.svelte';
  import Input from '#lib/components/ui/Input.svelte';
  import { errorMessage } from '#lib/i18n/errors.ts';
  import { apiLanguage } from '#lib/i18n/language.ts';
  import { SLOT, aroundSlot } from '#lib/i18n/slot.ts';
  import { m } from '#lib/paraglide/messages.js';
  import type { PageProps } from './$types';

  let { data }: PageProps = $props();

  let email = $state('');
  let status: 'idle' | 'sending' | 'sent' = $state('idle');
  /** A problem with the email itself, shown on the field */
  let emailError: string | undefined = $state();
  /** Anything else, e.g. the API being unreachable */
  let formError: string | undefined = $state();

  async function submit(event: SubmitEvent) {
    event.preventDefault();
    status = 'sending';
    emailError = undefined;
    formError = undefined;

    try {
      // The link takes them there once signed in, on whatever device they open it
      await requestMagicLink(email, apiLanguage(), data.redirectTo);
      status = 'sent';
    } catch (e) {
      status = 'idle';
      if (e instanceof ApiError && e.status === 400) {
        emailError = m.login_invalid_email();
      } else {
        formError = errorMessage(e);
      }
    }
  }
</script>

<svelte:head>
  <title>{m.login_title()} · InvoHub</title>
</svelte:head>

{#if status === 'sent'}
  {@const [before, after] = aroundSlot(m.login_sent_detail({ email: SLOT }))}
  <h2 class="text-xl font-semibold">{m.login_sent_title()}</h2>
  <p class="mt-2 text-sm text-gray-600">
    {before}<span class="font-medium text-gray-900">{email}</span>{after}
  </p>
  <Button variant="link" class="mt-6" onclick={() => (status = 'idle')}>{m.login_use_different_email()}</Button>
{:else}
  <h2 class="text-xl font-semibold">{m.login_title()}</h2>

  <form class="mt-4 space-y-4" onsubmit={submit}>
    <Input
      label={m.login_email()}
      type="email"
      name="email"
      required
      autocomplete="email"
      autofocus
      bind:value={email}
      error={emailError}
    />

    {#if formError}
      <p class="text-sm text-red-600" role="alert">{formError}</p>
    {/if}

    <Button type="submit" fullWidth loading={status === 'sending'}>
      {status === 'sending' ? m.login_sending() : m.login_submit()}
    </Button>
  </form>
{/if}
