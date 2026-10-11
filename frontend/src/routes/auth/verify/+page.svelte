<script lang="ts">
  import { onMount } from 'svelte';
  import { goto, replaceState } from '$app/navigation';
  import { resolve } from '$app/paths';
  import { page } from '$app/state';
  import { postJson } from '#lib/api.ts';

  type Status = 'verifying' | 'invalid' | 'locked' | 'error';

  let status: Status = $state('verifying');

  // The magic link lands here instead of on the API: email scanners prefetch links, but they don't run this
  // script, so they can't use up the single-use token before the user does
  onMount(async () => {
    const token = page.url.searchParams.get('token');
    // Keep the token out of the address bar and history
    replaceState(resolve('/auth/verify'), {});

    if (!token) {
      status = 'invalid';
      return;
    }

    try {
      const response = await postJson('/api/auth/verify', { token });
      if (response.ok) {
        await goto(resolve('/'), { replaceState: true });
      } else if (response.status === 401) {
        status = 'invalid';
      } else if (response.status === 403 && (await response.json()).detail === 'This account is locked.') {
        status = 'locked';
      } else {
        status = 'error';
      }
    } catch {
      status = 'error';
    }
  });
</script>

<main class="container mx-auto px-4 py-16 text-center">
  {#if status === 'verifying'}
    <p>Signing you in…</p>
  {:else if status === 'invalid'}
    <h2 class="text-xl font-semibold">This sign-in link is invalid or has expired</h2>
    <p class="mt-2">Request a new sign-in link and try again.</p>
  {:else if status === 'locked'}
    <h2 class="text-xl font-semibold">This account is locked</h2>
    <p class="mt-2">Please contact support.</p>
  {:else}
    <h2 class="text-xl font-semibold">Something went wrong</h2>
    <p class="mt-2">Please try the link again in a moment.</p>
  {/if}
</main>
