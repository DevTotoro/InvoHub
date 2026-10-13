<script lang="ts">
  import { onMount } from 'svelte';
  import { goto, replaceState } from '$app/navigation';
  import { resolve } from '$app/paths';
  import { renewMagicLink, verifyMagicLink } from '#lib/api/auth.ts';
  import { ApiError } from '#lib/api/client.ts';
  import Button from '#lib/components/ui/Button.svelte';
  import Link from '#lib/components/ui/Link.svelte';
  import { apiLanguage } from '#lib/i18n/language.ts';
  import { m } from '#lib/paraglide/messages.js';

  import type { PageProps } from './$types';

  let { data }: PageProps = $props();

  type Status = 'ready' | 'verifying' | 'invalid' | 'expired' | 'renewing' | 'renewed' | 'suspended' | 'error';

  // Read once: the address bar is cleared right away, but the token stays here until the user continues
  // svelte-ignore state_referenced_locally
  const { token, redirectTo, signedIn } = data;

  let status: Status = $state(token ? 'ready' : 'invalid');

  onMount(() => {
    // Keep the token out of the address bar and history
    replaceState(resolve('/(auth)/auth/verify'), {});
    if (signedIn) signIn();
  });

  // Signing in waits for a click: email scanners open links (some even run scripts) before the user does,
  // and must not use up the single-use token. None of them press buttons.
  async function signIn() {
    if (!token) return;
    status = 'verifying';

    try {
      await verifyMagicLink(token, apiLanguage());
    } catch (e) {
      if (e instanceof ApiError && e.code === 'expired_link') {
        status = 'expired';
      } else if (e instanceof ApiError && e.code === 'invalid_link') {
        status = 'invalid';
      } else if (e instanceof ApiError && e.code === 'account_suspended') {
        status = 'suspended';
      } else {
        status = 'error';
      }
      return;
    }

    // Where the link leads, e.g. the invitations for a link from an invitation email.
    // invalidateAll: the session is new, so everything loaded so far was loaded signed out.
    // resolve() only takes route IDs; redirectTo is a path that safeRedirect() already validated.
    // eslint-disable-next-line svelte/no-navigation-without-resolve
    await goto(redirectTo, { replaceState: true, invalidateAll: true });
  }

  /** A fresh link in place of the expired one, to the same address and leading to the same place */
  async function renew() {
    if (!token) return;
    status = 'renewing';
    try {
      await renewMagicLink(token, apiLanguage(), redirectTo);
      status = 'renewed';
    } catch (e) {
      status = e instanceof ApiError && e.code === 'invalid_link' ? 'invalid' : 'error';
    }
  }
</script>

<svelte:head>
  <title>{m.login_title()} · InvoHub</title>
</svelte:head>

{#if status === 'ready' || status === 'verifying'}
  <h2 class="text-xl font-semibold">{m.login_title()}</h2>
  <p class="mt-2 text-sm text-gray-600">{m.verify_prompt()}</p>
  <Button fullWidth class="mt-4" loading={status === 'verifying'} onclick={signIn}>
    {status === 'verifying' ? m.verify_signing_in() : m.verify_continue()}
  </Button>
{:else if status === 'expired' || status === 'renewing'}
  <h2 class="text-xl font-semibold">{m.verify_expired_title()}</h2>
  <Button fullWidth class="mt-4" loading={status === 'renewing'} onclick={renew}>{m.verify_renew()}</Button>
{:else if status === 'renewed'}
  <h2 class="text-xl font-semibold">{m.verify_renewed_title()}</h2>
  <p class="mt-2 text-sm text-gray-600">{m.verify_renewed_detail()}</p>
{:else if status === 'invalid'}
  <h2 class="text-xl font-semibold">{m.verify_invalid_title()}</h2>
  <Link href={resolve('/(auth)/login')} class="mt-6">{m.verify_request_new()}</Link>
{:else if status === 'suspended'}
  <h2 class="text-xl font-semibold">{m.verify_suspended_title()}</h2>
  <p class="mt-2 text-sm text-gray-600">{m.verify_suspended_detail()}</p>
{:else}
  <h2 class="text-xl font-semibold">{m.error_page_something_went_wrong()}</h2>
  <Button variant="link" class="mt-6" onclick={signIn}>{m.verify_try_again()}</Button>
{/if}
