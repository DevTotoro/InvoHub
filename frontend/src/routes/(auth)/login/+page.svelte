<script lang="ts">
  import { requestMagicLink } from '#lib/api/auth.ts';
  import { ApiError } from '#lib/api/client.ts';
  import { rememberRedirect } from '#lib/auth/redirect.ts';
  import Button from '#lib/components/ui/Button.svelte';
  import Input from '#lib/components/ui/Input.svelte';
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
      // The link opens in a new tab, so remember where to go once it is used
      rememberRedirect(data.redirectTo);
      await requestMagicLink(email);
      status = 'sent';
    } catch (e) {
      status = 'idle';
      if (e instanceof ApiError && e.status === 400) {
        emailError = 'Enter a valid email address.';
      } else {
        formError = e instanceof ApiError ? e.message : 'Something went wrong. Please try again.';
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
  <Button variant="link" class="mt-6" onclick={() => (status = 'idle')}>Use a different email</Button>
{:else}
  <h2 class="text-xl font-semibold">Sign in to InvoHub</h2>
  <p class="mt-2 text-sm text-gray-600">
    Enter your email and we'll send you a sign-in link. New here? The same link creates your account.
  </p>

  <form class="mt-6 space-y-4" onsubmit={submit}>
    <Input
      label="Email"
      type="email"
      name="email"
      required
      autocomplete="email"
      bind:value={email}
      error={emailError}
    />

    {#if formError}
      <p class="text-sm text-red-600" role="alert">{formError}</p>
    {/if}

    <Button type="submit" fullWidth loading={status === 'sending'}>
      {status === 'sending' ? 'Sending…' : 'Email me a sign-in link'}
    </Button>
  </form>
{/if}
