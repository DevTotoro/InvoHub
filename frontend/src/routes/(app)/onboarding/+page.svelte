<script lang="ts">
  import { goto } from '$app/navigation';
  import { resolve } from '$app/paths';
  import { ApiError } from '#lib/api/client.ts';
  import { createCompany, invalidInput } from '#lib/api/companies.ts';
  import { rememberCompany } from '#lib/company/last-company.ts';
  import Button from '#lib/components/ui/Button.svelte';
  import Input from '#lib/components/ui/Input.svelte';
  import Link from '#lib/components/ui/Link.svelte';
  import Select from '#lib/components/ui/Select.svelte';
  import { countryName, currencyName } from '#lib/format/names.ts';
  import type { PageProps } from './$types';

  let { data }: PageProps = $props();

  // The first market
  const DEFAULT_COUNTRY = 'GR';

  /** Messages for the backend's invalid_input field and reason */
  const INVALID_INPUT: Record<string, string> = {
    'name.required': 'Enter a company name.',
    'name.too_long': 'Use at most 200 characters.',
    'country.unknown': 'Choose a country from the list.'
  };

  const title = $derived(data.firstCompany ? 'Set up your company' : 'New company');
  const countryOptions = $derived(
    data.countries
      .map((c) => ({ value: c.code, label: countryName(c.code) }))
      .sort((a, b) => a.label.localeCompare(b.label))
  );

  let name = $state('');
  let country = $state(DEFAULT_COUNTRY);
  let saving = $state(false);
  let nameError: string | undefined = $state();
  let countryError: string | undefined = $state();
  let formError: string | undefined = $state();

  const selected = $derived(data.countries.find((c) => c.code === country));

  async function submit(event: SubmitEvent) {
    event.preventDefault();
    saving = true;
    nameError = countryError = formError = undefined;

    try {
      const company = await createCompany({ name, country });
      rememberCompany(company.id);
      // invalidateAll: the user's list of companies has changed
      await goto(resolve('/(app)/c/[companyId]', { companyId: company.id }), { invalidateAll: true });
    } catch (e) {
      const invalid = invalidInput(e);
      const message = invalid && (INVALID_INPUT[`${invalid.field}.${invalid.reason}`] ?? 'Check this field.');
      if (invalid?.field === 'name') {
        nameError = message;
      } else if (invalid?.field === 'country') {
        countryError = message;
      } else {
        formError = e instanceof ApiError ? e.message : 'Something went wrong. Please try again.';
      }
    } finally {
      saving = false;
    }
  }
</script>

<svelte:head>
  <title>{title} · InvoHub</title>
</svelte:head>

<main class="flex justify-center px-4 py-16">
  <div class="w-full max-w-sm">
    <h2 class="text-xl font-semibold">{title}</h2>

    <form class="mt-4 space-y-4" onsubmit={submit}>
      <Input
        label="Company name"
        name="name"
        required
        maxlength={200}
        autocomplete="organization"
        autofocus
        bind:value={name}
        error={nameError}
      />

      <Select
        label="Country"
        name="country"
        options={countryOptions}
        bind:value={country}
        error={countryError}
        hint={selected && `Currency: ${currencyName(selected.currency)}`}
      />

      {#if selected && !selected.invoicingSupported}
        <p class="text-sm text-gray-600" role="status">
          Invoicing in {countryName(selected.code)} isn't available yet. You can set up your company and team now, and start
          invoicing once {countryName(selected.code)} is supported.
        </p>
      {/if}

      {#if formError}
        <p class="text-sm text-red-600" role="alert">{formError}</p>
      {/if}

      <Button type="submit" fullWidth loading={saving}>{saving ? 'Creating…' : 'Create company'}</Button>
    </form>

    {#if !data.firstCompany}
      <Link href={resolve('/(app)')} class="mt-4">Cancel</Link>
    {/if}
  </div>
</main>
