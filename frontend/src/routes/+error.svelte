<script lang="ts">
  import { resolve } from '$app/paths';
  import { page } from '$app/state';
  import Link from '#lib/components/ui/Link.svelte';

  const notFound = $derived(page.status === 404);
</script>

<svelte:head>
  <title>{notFound ? 'Not found' : 'Error'} · InvoHub</title>
</svelte:head>

<main class="container mx-auto px-4 py-16 text-center">
  <h2 class="text-xl font-semibold">{notFound ? 'Page not found' : 'Something went wrong'}</h2>
  <p class="mt-2 text-gray-600">
    {notFound ? "The page you're looking for doesn't exist." : page.error?.message}
  </p>
  <Link href={resolve('/(app)')} class="mt-6">Go to InvoHub</Link>
</main>
