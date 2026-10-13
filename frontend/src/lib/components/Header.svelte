<script lang="ts">
  import { resolve } from '$app/paths';
  import { page } from '$app/state';
  import CompanySwitcher from '#lib/components/CompanySwitcher.svelte';
  import LanguagePicker from '#lib/components/LanguagePicker.svelte';
  import UserMenu from '#lib/components/UserMenu.svelte';
  import GitHubIcon from '#lib/components/icons/GitHubIcon.svelte';
  import Link from '#lib/components/ui/Link.svelte';
  import { m } from '#lib/paraglide/messages.js';

  // Set by the (app) layout on signed-in pages, and by the company layout on company pages
  const user = $derived(page.data.user);
  const company = $derived(page.data.company);
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
      <!-- Signed in, the language is in the user menu; signed out, it stands on its own -->
      {#if user}
        <UserMenu {user} />
      {:else}
        <LanguagePicker />
      {/if}

      <!-- Left out on phones, where the header needs the room -->
      <div class="hidden sm:flex">
        <Link href="https://github.com/DevTotoro/InvoHub" variant="icon" external aria-label={m.header_github()}>
          <GitHubIcon class="size-5" />
        </Link>
      </div>
    </div>
  </div>
</header>
