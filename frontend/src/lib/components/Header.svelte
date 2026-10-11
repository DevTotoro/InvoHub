<script lang="ts">
  import { goto } from '$app/navigation';
  import { resolve } from '$app/paths';
  import { page } from '$app/state';
  import { logout } from '#lib/api/auth.ts';
  import github from '#lib/assets/github.svg';

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
    <h1 class="text-2xl font-bold"><a href={resolve('/(app)')}>InvoHub</a></h1>

    <div class="flex items-center gap-4">
      {#if user}
        <span class="hidden text-sm text-gray-600 sm:inline">{user.email}</span>
        <button
          type="button"
          onclick={signOut}
          disabled={signingOut}
          class="text-sm font-medium text-gray-700 hover:text-gray-900 disabled:opacity-60"
        >
          {signOutFailed ? 'Sign out failed, retry' : 'Sign out'}
        </button>
      {/if}

      <a href="https://github.com/DevTotoro/InvoHub" target="_blank" rel="noopener noreferrer">
        <img src={github} alt="GitHub" class="h-6 w-6" />
      </a>
    </div>
  </div>
</header>
