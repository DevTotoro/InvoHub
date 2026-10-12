<script lang="ts">
  import { goto } from '$app/navigation';
  import { resolve } from '$app/paths';
  import { page } from '$app/state';
  import { logout } from '#lib/api/auth.ts';
  import CompanySwitcher from '#lib/components/CompanySwitcher.svelte';
  import LanguagePicker from '#lib/components/LanguagePicker.svelte';
  import GitHubIcon from '#lib/components/icons/GitHubIcon.svelte';
  import Button from '#lib/components/ui/Button.svelte';
  import Link from '#lib/components/ui/Link.svelte';
  import { m } from '#lib/paraglide/messages.js';

  // Set by the (app) layout on signed-in pages, and by the company layout on company pages
  const user = $derived(page.data.user);
  const company = $derived(page.data.company);

  let signingOut = $state(false);
  let signOutFailed = $state(false);

  async function signOut() {
    signingOut = true;
    signOutFailed = false;
    try {
      await logout();
      await goto(resolve('/(auth)/login'), { invalidateAll: true });
    } catch {
      signOutFailed = true;
    } finally {
      signingOut = false;
    }
  }
</script>

<header class="w-full">
  <div class="container mx-auto flex h-16 items-center justify-between space-x-2 px-4">
    <div class="flex min-w-0 items-center gap-2">
      <h1 class="shrink-0 text-2xl font-bold"><Link href={resolve('/(app)')} variant="plain">InvoHub</Link></h1>
      {#if user && company}
        <CompanySwitcher current={company} companies={user.companies} />
      {/if}
    </div>

    <div class="flex shrink-0 items-center gap-2 sm:gap-4">
      {#if user}
        {#if user.invitations.length > 0}
          <!-- On small screens just an envelope and the count; the label says it in full for screen readers -->
          <Link
            href={resolve('/(app)/invitations')}
            variant="ghost"
            aria-label={m.header_invitations({ count: user.invitations.length })}
          >
            <svg class="size-5 sm:hidden" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
              <path d="M3 4a2 2 0 0 0-2 2v1.16l8.33 4.17a1.5 1.5 0 0 0 1.34 0L19 7.16V6a2 2 0 0 0-2-2H3Z" />
              <path d="m19 8.84-7.67 3.83a3 3 0 0 1-2.66 0L1 8.84V14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V8.84Z" />
            </svg>
            <span aria-hidden="true" class="sm:hidden">{user.invitations.length}</span>
            <span aria-hidden="true" class="hidden sm:inline"
              >{m.header_invitations({ count: user.invitations.length })}</span
            >
          </Link>
        {/if}
        <span class="hidden text-sm text-gray-600 lg:inline">{user.email}</span>
        <Button variant="ghost" onclick={signOut} loading={signingOut}>
          {signOutFailed ? m.header_sign_out_failed() : m.header_sign_out()}
        </Button>
      {/if}

      <LanguagePicker signedIn={!!user} />

      <!-- Left out on phones, where the header needs the room -->
      <div class="hidden sm:flex">
        <Link href="https://github.com/DevTotoro/InvoHub" variant="icon" external aria-label={m.header_github()}>
          <GitHubIcon class="size-5" />
        </Link>
      </div>
    </div>
  </div>
</header>
