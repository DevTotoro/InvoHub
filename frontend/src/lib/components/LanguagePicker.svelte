<script lang="ts">
  import { setLanguage } from '#lib/api/auth.ts';
  import { LANGUAGES, apiLanguage, switchLanguage } from '#lib/i18n/language.ts';
  import { m } from '#lib/paraglide/messages.js';
  import { getLocale, type Locale } from '#lib/paraglide/runtime.js';

  interface Props {
    /** Signed in, the choice is also saved on the account: it follows the user to other devices and their emails */
    signedIn: boolean;
  }

  let { signedIn }: Props = $props();

  const current = getLocale();
  let value: Locale = $state(current);
  let saving = $state(false);
  const selected = $derived(LANGUAGES.find((l) => l.locale === value)!);

  async function change() {
    saving = true;
    try {
      if (signedIn) await setLanguage(apiLanguage(value));
      switchLanguage(value);
    } catch {
      // Not saved, so stay in the current language rather than switch for this browser only
      value = current;
      saving = false;
    }
  }
</script>

<!--
  The select is see-through and laid over its own label, so it opens the browser's native list (and works with
  screen readers and the keyboard), while the label can be shorter than an option on small screens.
-->
<div
  class={[
    'relative inline-flex items-center gap-1 px-2 py-2 text-sm text-gray-700 transition-colors',
    'hover:bg-gray-100 hover:text-gray-900',
    'has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-taupe-600',
    saving && 'opacity-60'
  ]}
>
  <span aria-hidden="true">
    <span class="sm:hidden">{selected.short}</span>
    <span class="hidden sm:inline">{selected.name}</span>
  </span>
  <svg class="size-4 shrink-0 text-gray-400" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
    <path
      fill-rule="evenodd"
      d="M5.22 8.22a.75.75 0 0 1 1.06 0L10 11.94l3.72-3.72a.75.75 0 1 1 1.06 1.06l-4.25 4.25a.75.75 0 0 1-1.06 0L5.22 9.28a.75.75 0 0 1 0-1.06Z"
      clip-rule="evenodd"
    />
  </svg>
  <select
    aria-label={m.header_language()}
    bind:value
    onchange={change}
    disabled={saving}
    class="absolute inset-0 cursor-pointer opacity-0 disabled:cursor-not-allowed"
  >
    {#each LANGUAGES as language (language.locale)}
      <option value={language.locale} lang={language.locale}>{language.name}</option>
    {/each}
  </select>
</div>
