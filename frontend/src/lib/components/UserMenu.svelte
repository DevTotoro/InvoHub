<script lang="ts">
  import { goto } from '$app/navigation';
  import { resolve } from '$app/paths';
  import { logout, type User } from '#lib/api/auth.ts';
  import Link from '#lib/components/ui/Link.svelte';
  import { m } from '#lib/paraglide/messages.js';

  interface Props {
    user: User;
  }

  let { user }: Props = $props();

  const menuId = $props.id();
  const invitations = $derived(user.invitations.length);

  let open = $state(false);
  let container: HTMLElement | undefined = $state();
  let signingOut = $state(false);
  let signOutFailed = $state(false);

  function close() {
    open = false;
  }

  function closeOnOutsideClick(event: MouseEvent) {
    if (open && container && !container.contains(event.target as Node)) close();
  }

  async function signOut() {
    signingOut = true;
    signOutFailed = false;
    try {
      await logout();
      close();
      await goto(resolve('/(auth)/login'), { invalidateAll: true });
    } catch {
      signOutFailed = true;
    } finally {
      signingOut = false;
    }
  }
</script>

<svelte:window onclick={closeOnOutsideClick} onkeydown={(e) => e.key === 'Escape' && close()} />

<div class="relative min-w-0" bind:this={container}>
  <!-- The email, cut short if long (whole in the tooltip and the menu); on phones an icon. Later the user's name. -->
  <button
    type="button"
    title={user.email}
    aria-label={invitations > 0 ? `${m.user_menu()}, ${m.header_invitations({ count: invitations })}` : m.user_menu()}
    aria-expanded={open}
    aria-controls={menuId}
    onclick={() => (open = !open)}
    class={[
      'flex min-w-0 items-center gap-1.5 px-2 py-2 text-sm text-gray-700 transition-colors',
      'hover:cursor-pointer hover:bg-gray-100 hover:text-gray-900',
      'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-taupe-600'
    ]}
  >
    <span aria-hidden="true" class="hidden max-w-56 truncate sm:inline">{user.email}</span>
    <svg class="size-5 shrink-0 sm:hidden" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
      <path
        d="M10 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6ZM3.465 14.493a1.23 1.23 0 0 0 .41 1.412A9.957 9.957 0 0 0 10 18c2.31 0 4.438-.784 6.131-2.1.43-.333.604-.903.408-1.41a7.002 7.002 0 0 0-13.074.003Z"
      />
    </svg>
    {#if invitations > 0}
      <span
        aria-hidden="true"
        class="shrink-0 rounded-full bg-red-600 px-1.5 text-xs leading-5 font-semibold text-white"
      >
        {invitations}
      </span>
    {/if}
    <svg class="size-4 shrink-0 text-gray-400" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
      <path
        fill-rule="evenodd"
        d="M5.22 8.22a.75.75 0 0 1 1.06 0L10 11.94l3.72-3.72a.75.75 0 1 1 1.06 1.06l-4.25 4.25a.75.75 0 0 1-1.06 0L5.22 9.28a.75.75 0 0 1 0-1.06Z"
        clip-rule="evenodd"
      />
    </svg>
  </button>

  {#if open}
    <div id={menuId} class="absolute right-0 z-10 mt-2 w-64 border border-gray-200 bg-white shadow-lg">
      <p class="truncate px-3 py-3 text-sm text-gray-500">{user.email}</p>

      <div class="border-t border-gray-200 py-1">
        <Link
          href={resolve('/(app)/invitations')}
          variant="plain"
          onclick={close}
          class="block px-3 py-2 text-sm text-gray-700 hover:bg-gray-100"
        >
          {invitations > 0 ? m.header_invitations({ count: invitations }) : m.header_invitations_none()}
        </Link>
      </div>

      <div class="border-t border-gray-200 py-1">
        <button
          type="button"
          disabled={signingOut}
          onclick={signOut}
          class="block w-full px-3 py-2 text-left text-sm text-red-600 hover:cursor-pointer hover:bg-red-50 disabled:opacity-60"
        >
          {signOutFailed ? m.header_sign_out_failed() : m.header_sign_out()}
        </button>
      </div>
    </div>
  {/if}
</div>
