<script lang="ts">
  import { countryName } from '#lib/format/names.ts';
  import type { PageProps } from './$types';

  let { data }: PageProps = $props();
</script>

<svelte:head>
  <title>{data.company.name} · InvoHub</title>
</svelte:head>

<main class="container mx-auto px-4 py-12">
  <h2 class="text-xl md:text-2xl">Welcome, <span class="font-semibold">{data.user.email}</span></h2>

  {#if !data.company.invoicingSupported}
    <p class="mt-4 text-sm text-gray-600">Invoicing in {countryName(data.company.country)} isn't available yet.</p>
  {/if}

  <section class="mt-8 text-center text-gray-500">Your invoices will show up here.</section>
</main>
