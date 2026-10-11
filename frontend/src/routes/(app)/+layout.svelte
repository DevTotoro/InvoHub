<script lang="ts">
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { resolve } from '$app/paths';
  import { page } from '$app/state';
  import { onSessionLost } from '#lib/api/client.ts';
  import type { LayoutProps } from './$types';

  let { children }: LayoutProps = $props();

  // If any API call finds the session gone (expired, signed out elsewhere, user suspended), leave the app
  // right away rather than leaving stale data on screen. Registered only while an (app) page is shown.
  onMount(() =>
    onSessionLost(() => {
      const returnTo = encodeURIComponent(page.url.pathname + page.url.search);
      // eslint-disable-next-line svelte/no-navigation-without-resolve -- resolved, plus a query string
      goto(`${resolve('/(auth)/login')}?redirectTo=${returnTo}`, { replaceState: true });
    })
  );
</script>

{@render children()}
