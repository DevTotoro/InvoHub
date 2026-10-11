<script lang="ts">
  import { resolve } from '$app/paths';
  import { page } from '$app/state';

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
  <a href={resolve('/(app)')} class="mt-6 inline-block text-sm font-medium text-indigo-600 hover:text-indigo-500">
    Go to InvoHub
  </a>
</main>
