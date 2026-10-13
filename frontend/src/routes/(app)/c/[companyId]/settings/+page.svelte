<script lang="ts">
  import { invalidate, invalidateAll } from '$app/navigation';
  import { invalidInput } from '#lib/api/companies.ts';
  import { closeCompany } from '#lib/api/ownership.ts';
  import {
    addBankAccount,
    removeBankAccount,
    removeLogo,
    updateBusinessDetails,
    updateCompanyProfile,
    uploadLogo,
    type BankAccount
  } from '#lib/api/settings.ts';
  import { canChange } from '#lib/company/permissions.ts';
  import { confirmAction } from '#lib/components/confirm.svelte.ts';
  import Button from '#lib/components/ui/Button.svelte';
  import Input from '#lib/components/ui/Input.svelte';
  import Select from '#lib/components/ui/Select.svelte';
  import { countryName, currencyName } from '#lib/format/names.ts';
  import { errorMessage } from '#lib/i18n/errors.ts';
  import { getLocale } from '#lib/paraglide/runtime.js';
  import { m } from '#lib/paraglide/messages.js';
  import type { PageProps } from './$types';

  let { data }: PageProps = $props();

  const LOGO_MAX_BYTES = 1024 * 1024;

  /** Messages for the backend's invalid_input field and reason; any other too_long or reason has a general one */
  const INVALID_INPUT: Record<string, () => string> = {
    'name.required': m.onboarding_name_required,
    'name.too_long': m.onboarding_name_too_long,
    'country.unknown': m.onboarding_country_unknown,
    'taxId.check_digit': m.settings_tax_id_check_digit,
    'taxId.invalid': m.settings_tax_id_invalid,
    'mainActivity.invalid': m.settings_main_activity_invalid,
    'bankName.required': m.settings_bank_name_required,
    'iban.invalid': m.settings_iban_invalid,
    'iban.duplicate': m.settings_iban_duplicate,
    'logo.too_large': m.settings_logo_too_large,
    'logo.unsupported_type': m.settings_logo_unsupported,
    'logo.empty': m.settings_logo_empty,
    'logo.upload_missing': m.settings_logo_upload_failed
  };
  /** What a value looks like, where that helps: a ΚΑΔ is a number, not its description */
  const COUNTRY_DETAIL_PLACEHOLDERS: Record<string, () => string> = {
    mainActivity: m.settings_main_activity_placeholder
  };
  const COUNTRY_DETAIL_LABELS: Record<string, () => string> = {
    taxOffice: m.settings_tax_office,
    mainActivity: m.settings_main_activity
  };

  const settings = $derived(data.settings);
  const company = $derived(settings.company);
  // Read-only, closed and suspended companies can be looked at, not changed
  const editable = $derived(canChange(company));
  const steps = $derived(new Map(settings.invoicingSetup.map((s) => [s.key, s])));
  /** Whether the country asks for business details; set for countries with invoicing */
  const businessStep = $derived(
    steps.has('tax_id') || steps.has('business_details')
      ? (steps.get('tax_id')?.done ?? true) && (steps.get('business_details')?.done ?? true)
      : undefined
  );
  const countryOptions = $derived(
    data.countries
      .map((c) => ({ value: c.code, label: countryName(c.code) }))
      .sort((a, b) => a.label.localeCompare(b.label, getLocale()))
  );

  // The forms start from what's saved, and again after each save
  let name = $derived(company.name);
  let country = $derived(company.country);
  let legalName = $derived(settings.legalName ?? '');
  let street = $derived(settings.address.street ?? '');
  let city = $derived(settings.address.city ?? '');
  let postalCode = $derived(settings.address.postalCode ?? '');
  let taxId = $derived(settings.taxId ?? '');
  let countryDetails = $derived(settings.countryDetails.map((d) => ({ key: d.key, value: d.value ?? '' })));
  let bankName = $state('');
  let iban = $state('');

  const selectedCountry = $derived(data.countries.find((c) => c.code === country));

  /** The section being saved */
  let busy: string | undefined = $state();
  /** The section last saved */
  let saved: string | undefined = $state();
  /** Per section: a message for the whole section */
  let sectionErrors: Record<string, string | undefined> = $state({});
  /** Per field: the backend's reason, as a message */
  let fieldErrors: Record<string, string | undefined> = $state({});

  async function run(section: string, action: () => Promise<unknown>) {
    busy = section;
    saved = undefined;
    sectionErrors = {};
    fieldErrors = {};
    try {
      await action();
      saved = section;
    } catch (e) {
      const invalid = invalidInput(e);
      if (invalid) {
        fieldErrors[invalid.field] = (
          INVALID_INPUT[`${invalid.field}.${invalid.reason}`] ??
          (invalid.reason === 'too_long' ? m.settings_too_long : m.onboarding_check_field)
        )();
      } else {
        sectionErrors[section] = errorMessage(e);
      }
    } finally {
      busy = undefined;
    }
  }

  async function saveProfile(event: SubmitEvent) {
    event.preventDefault();
    const clears = country !== company.country && (settings.taxId || settings.countryDetails.some((d) => d.value));
    if (clears && !(await confirmAction(m.settings_country_confirm(), m.settings_country_change()))) return;
    return run('company', async () => {
      await updateCompanyProfile(company.id, { name, country });
      // The name and country show across the app
      await invalidateAll();
    });
  }

  function saveBusinessDetails(event: SubmitEvent) {
    event.preventDefault();
    return run('business', async () => {
      await updateBusinessDetails(company.id, { legalName, street, city, postalCode, taxId, countryDetails });
      await invalidate('app:company-settings');
    });
  }

  function addAccount(event: SubmitEvent) {
    event.preventDefault();
    return run('bank', async () => {
      await addBankAccount(company.id, { bankName, iban });
      bankName = iban = '';
      await invalidate('app:company-settings');
    });
  }

  async function removeAccount(account: BankAccount) {
    const shown = formatIban(account.iban);
    if (!(await confirmAction(m.settings_bank_remove_confirm({ iban: shown }), m.settings_bank_remove()))) return;
    return run('bank', async () => {
      await removeBankAccount(company.id, account.id);
      await invalidate('app:company-settings');
    });
  }

  function changeLogo(event: Event & { currentTarget: HTMLInputElement }) {
    const input = event.currentTarget;
    const file = input.files?.[0];
    // Choosing the same file again should upload it again
    input.value = '';
    if (!file) return;
    if (file.size > LOGO_MAX_BYTES) {
      // Said straight away, without sending the file
      fieldErrors = { logo: m.settings_logo_too_large() };
      return;
    }
    return run('logo', async () => {
      await uploadLogo(company.id, file);
      await invalidate('app:company-settings');
    });
  }

  async function deleteLogo() {
    if (!(await confirmAction(m.settings_logo_remove_confirm(), m.settings_logo_remove()))) return;
    return run('logo', async () => {
      await removeLogo(company.id);
      await invalidate('app:company-settings');
    });
  }

  async function close() {
    if (!(await confirmAction(m.settings_close_confirm({ company: company.name }), m.settings_close_title()))) return;
    return run('close', async () => {
      await closeCompany(company.id);
      // Closed shows across the app: the switcher, the banner, every page's controls
      await invalidateAll();
    });
  }

  /** In groups of four, as printed on cards and statements */
  function formatIban(value: string) {
    return value.replace(/(.{4})/g, '$1 ').trim();
  }
</script>

{#snippet marker(state: 'done' | 'needed' | 'optional' | 'soon')}
  <span
    class={[
      'text-xs font-medium',
      state === 'done' && 'text-green-700',
      state === 'needed' && 'text-amber-700',
      (state === 'optional' || state === 'soon') && 'text-gray-500'
    ]}
  >
    {#if state === 'done'}✓ {m.settings_done()}{:else if state === 'needed'}{m.settings_needed()}{:else if state === 'optional'}{m.settings_optional()}{:else}{m.settings_coming_soon()}{/if}
  </span>
{/snippet}

{#snippet heading(id: string, title: string, state?: 'done' | 'needed' | 'optional' | 'soon')}
  <div class="flex items-baseline justify-between gap-4 border-b border-gray-200 pb-2">
    <h3 {id} class="font-semibold">{title}</h3>
    {#if state}{@render marker(state)}{/if}
  </div>
{/snippet}

{#snippet status(section: string)}
  {#if sectionErrors[section]}
    <p class="text-sm text-red-600" role="alert">{sectionErrors[section]}</p>
  {:else if saved === section}
    <p class="text-sm text-gray-600" role="status">{m.settings_saved()}</p>
  {/if}
{/snippet}

<svelte:head>
  <title>{m.settings_title()} · {company.name} · InvoHub</title>
</svelte:head>

<main class="container mx-auto px-4 py-8">
  <h2 class="text-xl font-semibold">{m.settings_title()}</h2>

  <!-- A closed company says so above every page of it -->
  {#if company.status === 'READ_ONLY' && !company.closedAt}
    <p class="mt-4 text-sm text-gray-600" role="status">{m.error_company_read_only()}</p>
  {/if}

  <fieldset disabled={!editable} class="mt-6 max-w-xl space-y-10">
    <section aria-labelledby="settings-company">
      {@render heading('settings-company', m.settings_company())}
      <form class="mt-4 space-y-4" onsubmit={saveProfile}>
        <Input
          label={m.onboarding_name()}
          name="name"
          required
          maxlength={200}
          autocomplete="organization"
          bind:value={name}
          error={fieldErrors.name}
        />
        <Select
          label={m.onboarding_country()}
          name="country"
          options={countryOptions}
          bind:value={country}
          error={fieldErrors.country}
          hint={selectedCountry && m.onboarding_currency({ currency: currencyName(selectedCountry.currency) })}
        />
        {@render status('company')}
        <Button type="submit" loading={busy === 'company'}>
          {busy === 'company' ? m.settings_saving() : m.settings_save()}
        </Button>
      </form>
    </section>

    <section aria-labelledby="settings-business">
      {@render heading(
        'settings-business',
        m.settings_business(),
        businessStep === undefined ? undefined : businessStep ? 'done' : 'needed'
      )}
      <form class="mt-4 space-y-4" onsubmit={saveBusinessDetails}>
        <Input
          label={m.settings_legal_name()}
          name="legalName"
          maxlength={255}
          bind:value={legalName}
          error={fieldErrors.legalName}
        />
        <Input
          label={m.settings_street()}
          name="street"
          maxlength={255}
          autocomplete="street-address"
          bind:value={street}
          error={fieldErrors.street}
        />
        <div class="grid gap-4 sm:grid-cols-2">
          <Input
            label={m.settings_city()}
            name="city"
            maxlength={100}
            autocomplete="address-level2"
            bind:value={city}
            error={fieldErrors.city}
          />
          <Input
            label={m.settings_postal_code()}
            name="postalCode"
            maxlength={20}
            autocomplete="postal-code"
            bind:value={postalCode}
            error={fieldErrors.postalCode}
          />
        </div>
        <Input
          label={company.country === 'GR' ? m.settings_tax_id_gr() : m.settings_tax_id()}
          name="taxId"
          maxlength={30}
          bind:value={taxId}
          error={fieldErrors.taxId}
        />
        {#each countryDetails as detail (detail.key)}
          <Input
            label={(COUNTRY_DETAIL_LABELS[detail.key] ?? (() => detail.key))()}
            name={detail.key}
            placeholder={COUNTRY_DETAIL_PLACEHOLDERS[detail.key]?.()}
            maxlength={100}
            bind:value={detail.value}
            error={fieldErrors[detail.key]}
          />
        {/each}
        {@render status('business')}
        <Button type="submit" loading={busy === 'business'}>
          {busy === 'business' ? m.settings_saving() : m.settings_save()}
        </Button>
      </form>
    </section>

    <section aria-labelledby="settings-bank">
      {@render heading('settings-bank', m.settings_bank_accounts(), 'optional')}
      {#if settings.bankAccounts.length === 0}
        <p class="mt-4 text-sm text-gray-500">{m.settings_bank_accounts_none()}</p>
      {:else}
        <ul aria-labelledby="settings-bank" class="mt-2 divide-y divide-gray-200">
          {#each settings.bankAccounts as account (account.id)}
            <li class="flex items-center justify-between gap-4 py-3">
              <div class="min-w-0">
                <p class="text-sm font-medium text-gray-900">{account.bankName}</p>
                <p class="truncate font-mono text-sm text-gray-600">{formatIban(account.iban)}</p>
              </div>
              <Button
                variant="ghost"
                aria-label={m.settings_bank_remove_of({ iban: formatIban(account.iban) })}
                disabled={busy === 'bank'}
                onclick={() => removeAccount(account)}
              >
                {m.settings_bank_remove()}
              </Button>
            </li>
          {/each}
        </ul>
      {/if}
      <form class="mt-4 grid gap-4 sm:grid-cols-[1fr_2fr]" onsubmit={addAccount}>
        <Input
          label={m.settings_bank_name()}
          name="bankName"
          required
          maxlength={100}
          bind:value={bankName}
          error={fieldErrors.bankName}
        />
        <Input
          label={m.settings_iban()}
          name="iban"
          required
          maxlength={50}
          autocomplete="off"
          spellcheck={false}
          bind:value={iban}
          error={fieldErrors.iban}
        />
        <div class="space-y-4 sm:col-span-2">
          {@render status('bank')}
          <Button type="submit" variant="secondary" loading={busy === 'bank'}>
            {busy === 'bank' ? m.settings_bank_adding() : m.settings_bank_add()}
          </Button>
        </div>
      </form>
    </section>

    <section aria-labelledby="settings-logo">
      {@render heading('settings-logo', m.settings_logo(), 'optional')}
      <div class="mt-4 flex flex-wrap items-center gap-4">
        {#if settings.logoUrl}
          <img
            src={settings.logoUrl}
            alt={m.settings_logo_alt({ company: company.name })}
            class="h-16 max-w-48 border border-gray-200 object-contain p-1"
          />
        {/if}
        <label
          class={[
            'inline-flex items-center border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-900',
            'focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-taupe-600',
            editable && busy !== 'logo' ? 'cursor-pointer hover:bg-gray-50' : 'cursor-not-allowed opacity-60'
          ]}
        >
          {settings.logoUrl ? m.settings_logo_replace() : m.settings_logo_upload()}
          <input
            type="file"
            name="logo"
            accept="image/png,image/jpeg,image/webp"
            class="sr-only"
            disabled={busy === 'logo'}
            aria-invalid={fieldErrors.logo ? true : undefined}
            onchange={changeLogo}
          />
        </label>
        {#if settings.logoUrl}
          <Button variant="ghost" disabled={busy === 'logo'} onclick={deleteLogo}>{m.settings_logo_remove()}</Button>
        {/if}
      </div>
      {#if fieldErrors.logo}
        <p class="mt-2 text-sm text-red-600" role="alert">{fieldErrors.logo}</p>
      {:else}
        <p class="mt-2 text-sm text-gray-500">{m.settings_logo_hint()}</p>
      {/if}
      {#if sectionErrors.logo}
        <p class="mt-2 text-sm text-red-600" role="alert">{sectionErrors.logo}</p>
      {/if}
    </section>

    {#if steps.has('mydata')}
      <section aria-labelledby="settings-mydata">
        {@render heading('settings-mydata', m.settings_mydata(), 'soon')}
      </section>
    {/if}
  </fieldset>

  <!-- Outside the fieldset: an unpaid company can still be closed -->
  {#if data.role === 'OWNER' && !company.closedAt && company.status !== 'SUSPENDED'}
    <section aria-labelledby="settings-close" class="mt-10 max-w-xl">
      {@render heading('settings-close', m.settings_close_title())}
      <p class="mt-4 text-sm text-gray-600">{m.settings_close_hint()}</p>
      {@render status('close')}
      <Button variant="secondary" class="mt-4" loading={busy === 'close'} onclick={close}>
        {m.settings_close_title()}
      </Button>
    </section>
  {/if}
</main>
