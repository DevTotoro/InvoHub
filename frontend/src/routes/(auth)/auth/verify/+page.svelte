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

  type Status = 'ready' | 'verifying' | 'invalid' | 'locked' | 'error';

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
      await verifyMagicLink(token);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        status = 'invalid';
      } else if (e instanceof ApiError && e.code === 'account_locked') {
        status = 'locked';
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
  <title>Sign in · InvoHub</title>
</svelte:head>

{#if status === 'ready' || status === 'verifying'}
  <h2 class="text-xl font-semibold">Sign in</h2>
  <p class="mt-2 text-sm text-gray-600">Continue to finish signing in to InvoHub.</p>
  <Button fullWidth class="mt-4" loading={status === 'verifying'} onclick={signIn}>
    {status === 'verifying' ? 'Signing in…' : 'Continue'}
  </Button>
{:else if status === 'invalid'}
  <h2 class="text-xl font-semibold">This sign-in link is invalid or has expired</h2>
  <p class="mt-2 text-sm text-gray-600">Links work once and expire after a while. Request a new one to sign in.</p>
  <Link href={resolve('/(auth)/login')} class="mt-6">Request a new link</Link>
{:else if status === 'locked'}
  <h2 class="text-xl font-semibold">This account is locked</h2>
  <p class="mt-2 text-sm text-gray-600">Please contact support.</p>
{:else}
  <h2 class="text-xl font-semibold">Something went wrong</h2>
  <p class="mt-2 text-sm text-gray-600">Please try again in a moment.</p>
  <Button variant="link" class="mt-6" onclick={signIn}>Try again</Button>
{/if}
