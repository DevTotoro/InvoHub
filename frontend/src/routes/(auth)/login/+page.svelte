<script lang="ts">
  import { requestMagicLink } from '#lib/api/auth.ts';
  import { ApiError } from '#lib/api/client.ts';
  import { rememberRedirect } from '#lib/auth/redirect.ts';
  import type { PageProps } from './$types';

  let { data }: PageProps = $props();

  let email = $state('');
  let status: 'idle' | 'sending' | 'sent' = $state('idle');
  let errorMessage: string | undefined = $state();

  async function submit(event: SubmitEvent) {
    event.preventDefault();
    status = 'sending';
    errorMessage = undefined;

    try {
      // The link opens in a new tab, so remember where to go once it is used
      rememberRedirect(data.redirectTo);
      await requestMagicLink(email);
      status = 'sent';
    } catch (e) {
      status = 'idle';
      if (e instanceof ApiError && e.status === 400) {
        errorMessage = 'Enter a valid email address.';
      } else {
        errorMessage = e instanceof ApiError ? e.message : 'Something went wrong. Please try again.';
      }
    }
  }
</script>

<svelte:head>
  <title>Sign in · InvoHub</title>
</svelte:head>

{#if status === 'sent'}
  <h2 class="text-xl font-semibold">Check your inbox</h2>
  <p class="mt-2 text-sm text-gray-600">
    We've emailed a sign-in link to <span class="font-medium text-gray-900">{email}</span>. Open it to continue.
  </p>
  <p class="mt-4 text-sm text-gray-600">
    Asked for one recently? That link still works, so look for it first, and check your spam folder.
  </p>
  <button
    type="button"
    class="mt-6 text-sm font-medium text-indigo-600 hover:text-indigo-500"
    onclick={() => (status = 'idle')}
  >
    Use a different email
  </button>
{:else}
  <h2 class="text-xl font-semibold">Sign in to InvoHub</h2>
  <p class="mt-2 text-sm text-gray-600">
    Enter your email and we'll send you a sign-in link. New here? The same link creates your account.
  </p>

  <form class="mt-6 space-y-4" onsubmit={submit}>
    <label class="block">
      <span class="text-sm font-medium">Email</span>
      <input
        type="email"
        name="email"
        required
        autocomplete="email"
        bind:value={email}
        class="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 focus:outline-none"
      />
    </label>

    {#if errorMessage}
      <p class="text-sm text-red-600" role="alert">{errorMessage}</p>
    {/if}

    <button
      type="submit"
      disabled={status === 'sending'}
      class="w-full rounded-lg bg-indigo-600 px-4 py-2 font-medium text-white hover:bg-indigo-500 disabled:opacity-60"
    >
      {status === 'sending' ? 'Sending…' : 'Email me a sign-in link'}
    </button>
  </form>
{/if}
