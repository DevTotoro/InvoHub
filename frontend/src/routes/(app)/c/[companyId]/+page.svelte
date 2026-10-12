<script lang="ts">
  import { countryName } from '#lib/format/names.ts';
  import { SLOT, aroundSlot } from '#lib/i18n/slot.ts';
  import { m } from '#lib/paraglide/messages.js';
  import type { PageProps } from './$types';

  let { data }: PageProps = $props();

  const welcome = aroundSlot(m.company_welcome({ email: SLOT }));
</script>

<svelte:head>
  <title>{data.company.name} · InvoHub</title>
</svelte:head>

<main class="container mx-auto px-4 py-12">
  <h2 class="text-xl md:text-2xl">{welcome[0]}<span class="font-semibold">{data.user.email}</span>{welcome[1]}</h2>

  {#if !data.company.invoicingSupported}
    <p class="mt-4 text-sm text-gray-600">
      {m.company_invoicing_unavailable({ country: countryName(data.company.country) })}
    </p>
  {/if}

  <section class="mt-8 text-center text-gray-500">{m.company_invoices_placeholder()}</section>
</main>
