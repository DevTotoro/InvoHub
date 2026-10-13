<script lang="ts">
  import './layout.css';

  import favicon from '#lib/assets/favicon.svg';

  import type { LayoutProps } from './$types';

  import ConfirmDialog from '#lib/components/ConfirmDialog.svelte';
  import Header from '#lib/components/Header.svelte';
  import { getLocale } from '#lib/paraglide/runtime.js';

  let { children }: LayoutProps = $props();

  // For screen readers, spell checking and hyphenation. Changing the language reloads the page, so once is enough
  document.documentElement.lang = getLocale();
</script>

<svelte:head>
  <link rel="icon" href={favicon} />
</svelte:head>

<Header />
{@render children()}
<ConfirmDialog />
