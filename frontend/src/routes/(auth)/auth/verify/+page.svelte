<script lang="ts">
  import { onMount } from 'svelte';
  import { goto, replaceState } from '$app/navigation';
  import { resolve } from '$app/paths';
  import { page } from '$app/state';
  import { verifyMagicLink } from '#lib/api/auth.ts';
  import { ApiError } from '#lib/api/client.ts';
  import { consumeRedirect } from '#lib/auth/redirect.ts';
  import Button from '#lib/components/ui/Button.svelte';
  import Link from '#lib/components/ui/Link.svelte';
  import { apiLanguage } from '#lib/i18n/language.ts';
  import { m } from '#lib/paraglide/messages.js';

  type Status = 'ready' | 'verifying' | 'invalid' | 'suspended' | 'error';

  // Read once: the address bar is cleared right away, but the token stays here until the user continues
  const token = page.url.searchParams.get('token');

  let status: Status = $state(token ? 'ready' : 'invalid');

  // Keep the token out of the address bar and history
  onMount(() => replaceState(resolve('/(auth)/auth/verify'), {}));

  // Signing in waits for a click: email scanners open links (some even run scripts) before the user does,
  // and must not use up the single-use token. None of them press buttons.
  async function signIn() {
    if (!token) return;
    status = 'verifying';

    try {
      await verifyMagicLink(token, apiLanguage());
    } catch (e) {
      if (e instanceof ApiError && e.code === 'invalid_link') {
        status = 'invalid';
      } else if (e instanceof ApiError && e.code === 'account_suspended') {
        status = 'suspended';
      } else {
        status = 'error';
      }
      return;
    }

    // invalidateAll: the session is new, so everything loaded so far was loaded signed out.
    // resolve() only takes route IDs; this is a stored path that consumeRedirect() already validated.
    // eslint-disable-next-line svelte/no-navigation-without-resolve
    await goto(consumeRedirect(), { replaceState: true, invalidateAll: true });
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
{:else if status === 'invalid'}
  <h2 class="text-xl font-semibold">{m.verify_invalid_title()}</h2>
  <p class="mt-2 text-sm text-gray-600">{m.verify_invalid_detail()}</p>
  <Link href={resolve('/(auth)/login')} class="mt-6">{m.verify_request_new()}</Link>
{:else if status === 'suspended'}
  <h2 class="text-xl font-semibold">{m.verify_suspended_title()}</h2>
  <p class="mt-2 text-sm text-gray-600">{m.verify_suspended_detail()}</p>
{:else}
  <h2 class="text-xl font-semibold">{m.error_page_something_went_wrong()}</h2>
  <p class="mt-2 text-sm text-gray-600">{m.verify_error_detail()}</p>
  <Button variant="link" class="mt-6" onclick={signIn}>{m.verify_try_again()}</Button>
{/if}
