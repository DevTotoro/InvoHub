<script lang="ts">
  import { untrack, type Snippet } from 'svelte';
  import { invalidInput, type Country } from '#lib/api/companies.ts';
  import type { ContactInput, ContactKind } from '#lib/api/contacts.ts';
  import Button from '#lib/components/ui/Button.svelte';
  import Input from '#lib/components/ui/Input.svelte';
  import Select from '#lib/components/ui/Select.svelte';
  import { countryName } from '#lib/format/names.ts';
  import { errorMessage } from '#lib/i18n/errors.ts';
  import { getLocale } from '#lib/paraglide/runtime.js';
  import { m } from '#lib/paraglide/messages.js';

  interface Props {
    countries: Country[];
    /** What the form starts from */
    initial: ContactInput;
    submitLabel: string;
    busyLabel: string;
    /** Saves; a refusal (invalid_input) shows on its field, anything else above the button */
    onsave: (input: ContactInput) => Promise<unknown>;
    /** Shown, but nothing can be changed */
    readonly?: boolean;
    /** More buttons, beside the submit button */
    actions?: Snippet;
  }

  let { countries, initial, submitLabel, busyLabel, onsave, readonly = false, actions }: Props = $props();

  /** Messages for the backend's invalid_input field and reason; any other too_long or reason has a general one */
  const INVALID_INPUT: Record<string, () => string> = {
    'name.required': m.contact_name_required,
    'country.unknown': m.onboarding_country_unknown,
    'taxId.check_digit': m.settings_tax_id_check_digit,
    'taxId.invalid': m.settings_tax_id_invalid,
    'email.invalid': m.invite_invalid_email,
    'phone.invalid': m.contact_phone_invalid
  };

  // A copy to edit, taken once: pages re-create the form ({#key}) to start it over from a saved contact
  let form: ContactInput = $state(untrack(() => ({ ...initial })));
  let saving = $state(false);
  let fieldErrors: Record<string, string | undefined> = $state({});
  let formError: string | undefined = $state();

  const kindName = $props.id();
  const kinds: { value: ContactKind; label: () => string }[] = [
    { value: 'BUSINESS', label: m.contact_kind_business },
    { value: 'PERSON', label: m.contact_kind_person }
  ];
  const countryOptions = $derived(
    countries
      .map((c) => ({ value: c.code, label: countryName(c.code) }))
      .sort((a, b) => a.label.localeCompare(b.label, getLocale()))
  );

  async function submit(event: SubmitEvent) {
    event.preventDefault();
    saving = true;
    fieldErrors = {};
    formError = undefined;
    try {
      await onsave(form);
    } catch (e) {
      const invalid = invalidInput(e);
      if (invalid) {
        fieldErrors[invalid.field] = (
          INVALID_INPUT[`${invalid.field}.${invalid.reason}`] ??
          (invalid.reason === 'too_long' ? m.settings_too_long : m.onboarding_check_field)
        )();
      } else {
        formError = errorMessage(e);
      }
    } finally {
      saving = false;
    }
  }
</script>

<form onsubmit={submit} class="max-w-xl">
  <fieldset disabled={readonly} class="space-y-4">
    <div>
      <span id="{kindName}-label" class="block text-sm font-medium text-gray-900">{m.contact_kind()}</span>
      <div role="radiogroup" aria-labelledby="{kindName}-label" class="mt-1 flex gap-4">
        {#each kinds as kind (kind.value)}
          <label class="flex items-center gap-2 text-sm text-gray-900">
            <input type="radio" name={kindName} value={kind.value} bind:group={form.kind} class="accent-taupe-600" />
            {kind.label()}
          </label>
        {/each}
      </div>
    </div>

    <Input
      label={m.contact_name()}
      name="name"
      required
      maxlength={200}
      autocomplete={form.kind === 'BUSINESS' ? 'organization' : 'name'}
      bind:value={form.name}
      error={fieldErrors.name}
    />
    <Select
      label={m.onboarding_country()}
      name="country"
      options={countryOptions}
      bind:value={form.country}
      error={fieldErrors.country}
    />
    <Input
      label={form.country === 'GR' ? m.settings_tax_id_gr() : m.settings_tax_id()}
      name="taxId"
      maxlength={30}
      bind:value={form.taxId}
      error={fieldErrors.taxId}
    />
    <div class="grid gap-4 sm:grid-cols-2">
      <Input
        label={m.contact_email()}
        name="email"
        type="email"
        maxlength={255}
        bind:value={form.email}
        error={fieldErrors.email}
      />
      <Input
        label={m.contact_phone()}
        name="phone"
        type="tel"
        maxlength={50}
        bind:value={form.phone}
        error={fieldErrors.phone}
      />
    </div>
    <Input
      label={m.settings_street()}
      name="street"
      maxlength={255}
      bind:value={form.street}
      error={fieldErrors.street}
    />
    <div class="grid gap-4 sm:grid-cols-2">
      <Input label={m.settings_city()} name="city" maxlength={100} bind:value={form.city} error={fieldErrors.city} />
      <Input
        label={m.settings_postal_code()}
        name="postalCode"
        maxlength={20}
        bind:value={form.postalCode}
        error={fieldErrors.postalCode}
      />
    </div>
    <div>
      <label for="{kindName}-notes" class="block text-sm font-medium text-gray-900">{m.contact_notes()}</label>
      <textarea
        id="{kindName}-notes"
        name="notes"
        rows="3"
        maxlength={2000}
        bind:value={form.notes}
        aria-invalid={fieldErrors.notes ? true : undefined}
        class="mt-1 block w-full border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 focus:border-taupe-500 focus:ring-2 focus:ring-taupe-500/20 focus:outline-none disabled:bg-gray-50"
      ></textarea>
      {#if fieldErrors.notes}
        <p class="mt-1 text-sm text-red-600" role="alert">{fieldErrors.notes}</p>
      {/if}
    </div>

    {#if formError}
      <p class="text-sm text-red-600" role="alert">{formError}</p>
    {/if}
  </fieldset>

  <div class="mt-6 flex flex-wrap items-center gap-2">
    {#if !readonly}
      <Button type="submit" loading={saving}>{saving ? busyLabel : submitLabel}</Button>
    {/if}
    {@render actions?.()}
  </div>
</form>
