<script lang="ts">
  import { resolve } from '$app/paths';
  import { page } from '$app/state';
  import Link from '#lib/components/ui/Link.svelte';
  import { m } from '#lib/paraglide/messages.js';

  const notFound = $derived(page.status === 404);
</script>

<svelte:head>
  <title>{notFound ? m.error_page_not_found_title() : m.error_page_title()} · InvoHub</title>
</svelte:head>

<main class="container mx-auto px-4 py-16 text-center">
  <h2 class="text-xl font-semibold">{notFound ? m.error_page_not_found() : m.error_page_something_went_wrong()}</h2>
  <p class="mt-2 text-gray-600">
    {notFound ? m.error_page_not_found_detail() : page.error?.message}
  </p>
  <Link href={resolve('/(app)')} class="mt-6">{m.error_page_go_home()}</Link>
</main>
