<script lang="ts">
  import { onMount } from 'svelte';
  import { goto, replaceState } from '$app/navigation';
  import { resolve } from '$app/paths';
  import { page } from '$app/state';
  import { verifyMagicLink } from '#lib/api/auth.ts';
  import { ApiError } from '#lib/api/client.ts';
  import { consumeRedirect } from '#lib/auth/redirect.ts';
  import Link from '#lib/components/ui/Link.svelte';

  type Status = 'verifying' | 'invalid' | 'locked' | 'error';

  let status: Status = $state('verifying');

  // The magic link lands here instead of on the API: email scanners prefetch links, but they don't run this
  // script, so they can't use up the single-use token before the user does
  onMount(async () => {
    const token = page.url.searchParams.get('token');
    // Keep the token out of the address bar and history
    replaceState(resolve('/(auth)/auth/verify'), {});

    if (!token) {
      status = 'invalid';
      return;
    }

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
  });
</script>

<svelte:head>
  <title>Signing in · InvoHub</title>
</svelte:head>

{#if status === 'verifying'}
  <p class="text-center text-gray-600">Signing you in…</p>
{:else if status === 'invalid'}
  <h2 class="text-xl font-semibold">This sign-in link is invalid or has expired</h2>
  <p class="mt-2 text-sm text-gray-600">Links work once and expire after a while. Request a new one to sign in.</p>
  <Link href={resolve('/(auth)/login')} class="mt-6">Request a new link</Link>
{:else if status === 'locked'}
  <h2 class="text-xl font-semibold">This account is locked</h2>
  <p class="mt-2 text-sm text-gray-600">Please contact support.</p>
{:else}
  <h2 class="text-xl font-semibold">Something went wrong</h2>
  <p class="mt-2 text-sm text-gray-600">Please open the link again in a moment.</p>
{/if}
