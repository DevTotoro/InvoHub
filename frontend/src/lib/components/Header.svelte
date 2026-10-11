<script lang="ts">
  import { goto } from '$app/navigation';
  import { resolve } from '$app/paths';
  import { page } from '$app/state';
  import { logout } from '#lib/api/auth.ts';
  import github from '#lib/assets/github.svg';
  import Button from '#lib/components/ui/Button.svelte';
  import Link from '#lib/components/ui/Link.svelte';

  // Set by the (app) layout on signed-in pages
  const user = $derived(page.data.user);

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
    <h1 class="text-2xl font-bold"><Link href={resolve('/(app)')} variant="plain">InvoHub</Link></h1>

    <div class="flex items-center gap-4">
      {#if user}
        <span class="hidden text-sm text-gray-600 sm:inline">{user.email}</span>
        <Button variant="ghost" onclick={signOut} loading={signingOut}>
          {signOutFailed ? 'Sign out failed, retry' : 'Sign out'}
        </Button>
      {/if}

      <Link href="https://github.com/DevTotoro/InvoHub" variant="plain" external>
        <img src={github} alt="GitHub" class="h-6 w-6" />
      </Link>
    </div>
  </div>
</header>
