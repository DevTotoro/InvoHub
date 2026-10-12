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

<select
  aria-label={m.header_language()}
  bind:value
  onchange={change}
  disabled={saving}
  class={[
    'bg-transparent px-2 py-2 text-sm text-gray-700 transition-colors hover:cursor-pointer hover:bg-gray-100',
    'hover:text-gray-900 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-taupe-600',
    'disabled:cursor-not-allowed disabled:opacity-60'
  ]}
>
  {#each LANGUAGES as language (language.locale)}
    <option value={language.locale} lang={language.locale}>{language.name}</option>
  {/each}
</select>
