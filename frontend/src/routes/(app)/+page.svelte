<script lang="ts">
  import type { PageProps } from './$types';

  let { data }: PageProps = $props();
</script>

<svelte:head>
  <title>InvoHub</title>
</svelte:head>

<main class="container mx-auto px-4 py-12">
  <h2 class="text-2xl font-semibold">Welcome</h2>
  <p class="mt-2 text-gray-600">Signed in as {data.user.email}.</p>

  <section class="mt-8 rounded-xl border border-dashed border-gray-300 p-10 text-center text-gray-500">
    Your invoices will show up here.
  </section>
</main>
