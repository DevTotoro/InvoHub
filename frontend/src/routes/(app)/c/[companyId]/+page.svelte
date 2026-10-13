<script lang="ts">
  import { resolve } from '$app/paths';
  import { setupPending, type SetupStep } from '#lib/api/settings.ts';
  import Link from '#lib/components/ui/Link.svelte';
  import { countryName } from '#lib/format/names.ts';
  import { SLOT, aroundSlot } from '#lib/i18n/slot.ts';
  import { m } from '#lib/paraglide/messages.js';
  import type { PageProps } from './$types';

  let { data }: PageProps = $props();

  const welcome = aroundSlot(m.company_welcome({ email: SLOT }));

  const STEP_LABELS: Record<string, () => string> = {
    tax_id: m.setup_step_tax_id,
    business_details: m.setup_step_business_details,
    mydata: m.setup_step_mydata
  };
  /** Shown to the owner and admins until every step they can do is done */
  const setup = $derived(
    data.settings && !data.company.closedAt && setupPending(data.settings.invoicingSetup)
      ? data.settings.invoicingSetup.filter((step) => step.key in STEP_LABELS)
      : []
  );
  const stepState = (step: SetupStep) =>
    !step.available ? m.settings_coming_soon() : step.done ? m.settings_done() : m.settings_needed();
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

  {#if setup.length > 0}
    <section aria-labelledby="setup-title" class="mt-8 max-w-md border border-gray-200 p-4">
      <h3 id="setup-title" class="font-semibold">{m.setup_title()}</h3>
      <ul class="mt-3 space-y-2">
        {#each setup as step (step.key)}
          <li class="flex justify-between gap-4 text-sm">
            <span class={step.done ? 'text-gray-500' : 'text-gray-900'}>
              {#if step.done}<span class="mr-1" aria-hidden="true">✓</span>{/if}{STEP_LABELS[step.key]()}
            </span>
            <span
              class={[
                'text-xs font-medium',
                step.available && !step.done ? 'text-amber-700' : step.done ? 'text-green-700' : 'text-gray-500'
              ]}>{stepState(step)}</span
            >
          </li>
        {/each}
      </ul>
      <Link href={resolve('/(app)/c/[companyId]/settings', { companyId: data.company.id })} class="mt-4">
        {m.setup_continue()}
      </Link>
    </section>
  {/if}

  <section class="mt-8 text-center text-gray-500">{m.company_invoices_placeholder()}</section>
</main>
