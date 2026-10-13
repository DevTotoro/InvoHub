<script lang="ts">
  import { goto } from '$app/navigation';
  import { resolve } from '$app/paths';
  import { logout, setLanguage, type User } from '#lib/api/auth.ts';
  import Link from '#lib/components/ui/Link.svelte';
  import { LANGUAGES, apiLanguage, switchLanguage } from '#lib/i18n/language.ts';
  import { m } from '#lib/paraglide/messages.js';
  import { getLocale, type Locale } from '#lib/paraglide/runtime.js';

  interface Props {
    user: User;
  }

  let { user }: Props = $props();

  const menuId = $props.id();
  const current = getLocale();
  const initial = $derived(user.email.charAt(0).toUpperCase());
  const invitations = $derived(user.invitations.length);

  let open = $state(false);
  let container: HTMLElement | undefined = $state();
  let switching = $state(false);
  let signingOut = $state(false);
  let signOutFailed = $state(false);

  function close() {
    open = false;
  }

  function closeOnOutsideClick(event: MouseEvent) {
    if (open && container && !container.contains(event.target as Node)) close();
  }

  /** Saved on the account, so it follows the user to other devices and their emails, then shown at once */
  async function pickLanguage(locale: Locale) {
    if (locale === current) return close();
    switching = true;
    try {
      await setLanguage(apiLanguage(locale));
      switchLanguage(locale);
    } catch {
      // Not saved: stay in the current language rather than switch for this browser only
      switching = false;
    }
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

<div class="relative" bind:this={container}>
  <button
    type="button"
    aria-label={invitations > 0 ? `${m.user_menu()}, ${m.header_invitations({ count: invitations })}` : m.user_menu()}
    aria-expanded={open}
    aria-controls={menuId}
    onclick={() => (open = !open)}
    class={[
      'relative flex size-9 items-center justify-center rounded-full bg-taupe-600 text-sm font-medium text-white',
      'transition-colors hover:cursor-pointer hover:bg-taupe-500',
      'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-taupe-600'
    ]}
  >
    <span aria-hidden="true">{initial}</span>
    {#if invitations > 0}
      <span
        aria-hidden="true"
        class="absolute -top-1 -right-1 flex size-5 items-center justify-center rounded-full bg-red-600 text-[11px] font-semibold text-white ring-2 ring-white"
      >
        {invitations}
      </span>
    {/if}
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

      <div class="border-t border-gray-200 py-1" role="group" aria-labelledby="{menuId}-language">
        <p id="{menuId}-language" class="px-3 pt-2 pb-1 text-xs font-medium text-gray-500">{m.header_language()}</p>
        {#each LANGUAGES as language (language.locale)}
          <button
            type="button"
            lang={language.locale}
            aria-pressed={language.locale === current}
            disabled={switching}
            onclick={() => pickLanguage(language.locale)}
            class="flex w-full items-center justify-between px-3 py-2 text-left text-sm text-gray-700 hover:cursor-pointer hover:bg-gray-100 disabled:opacity-60"
          >
            {language.name}
            {#if language.locale === current}
              <svg class="size-4 text-taupe-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                <path
                  fill-rule="evenodd"
                  d="M16.7 5.3a1 1 0 0 1 0 1.4l-8 8a1 1 0 0 1-1.4 0l-4-4a1 1 0 1 1 1.4-1.4L8 12.58l7.3-7.3a1 1 0 0 1 1.4 0Z"
                  clip-rule="evenodd"
                />
              </svg>
            {/if}
          </button>
        {/each}
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
