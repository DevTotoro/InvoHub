<script lang="ts">
  import { invalidate } from '$app/navigation';
  import { resolve } from '$app/paths';
  import { archiveContact, restoreContact, updateContact, type ContactInput } from '#lib/api/contacts.ts';
  import { canEdit } from '#lib/company/permissions.ts';
  import ContactForm from '#lib/components/ContactForm.svelte';
  import { confirmAction } from '#lib/components/confirm.svelte.ts';
  import Button from '#lib/components/ui/Button.svelte';
  import Link from '#lib/components/ui/Link.svelte';
  import { errorMessage } from '#lib/i18n/errors.ts';
  import { m } from '#lib/paraglide/messages.js';
  import type { PageProps } from './$types';

  let { data }: PageProps = $props();

  const params = $derived({ companyId: data.company.id });
  const editable = $derived(canEdit(data.role, data.company));
  const contact = $derived(data.contact);
  const saved = $derived<ContactInput>({
    kind: contact.kind,
    name: contact.name,
    country: contact.country,
    taxId: contact.taxId ?? '',
    email: contact.email ?? '',
    phone: contact.phone ?? '',
    street: contact.address.street ?? '',
    city: contact.address.city ?? '',
    postalCode: contact.address.postalCode ?? '',
    notes: contact.notes ?? ''
  });

  let busy = $state(false);
  let error: string | undefined = $state();
  /** Bumped after each save, so the form starts over from what was saved (cleaned up by the backend) */
  let version = $state(0);
  let justSaved = $state(false);

  async function save(input: ContactInput) {
    justSaved = false;
    await updateContact(data.company.id, contact.id, input);
    await invalidate('app:contact');
    version++;
    justSaved = true;
  }

  async function toggleArchived() {
    if (!contact.archivedAt) {
      if (!(await confirmAction(m.contact_archive_confirm({ name: contact.name }), m.contact_archive()))) return;
    }
    busy = true;
    error = undefined;
    try {
      await (contact.archivedAt ? restoreContact : archiveContact)(data.company.id, contact.id);
      await invalidate('app:contact');
    } catch (e) {
      error = errorMessage(e);
    } finally {
      busy = false;
    }
  }
</script>

<svelte:head>
  <title>{contact.name} · {data.company.name} · InvoHub</title>
</svelte:head>

<main class="container mx-auto px-4 py-8">
  <Link href={resolve('/(app)/c/[companyId]/contacts', params)} class="text-sm">{m.contact_back()}</Link>
  <h2 class="mt-2 text-xl font-semibold">{contact.name}</h2>
  {#if contact.archivedAt}
    <p class="mt-2 text-sm text-gray-600" role="status">{m.contact_archived_note()}</p>
  {/if}
  {#if error}
    <p class="mt-2 text-sm text-red-600" role="alert">{error}</p>
  {/if}

  <div class="mt-6">
    {#key version}
      <ContactForm
        countries={data.countries}
        initial={saved}
        submitLabel={m.settings_save()}
        busyLabel={m.settings_saving()}
        onsave={save}
        readonly={!editable}
      >
        {#snippet actions()}
          {#if editable}
            <Button variant="ghost" loading={busy} onclick={toggleArchived}>
              {contact.archivedAt ? m.contact_restore() : m.contact_archive()}
            </Button>
          {/if}
          {#if justSaved}
            <p class="text-sm text-gray-600" role="status">{m.settings_saved()}</p>
          {/if}
        {/snippet}
      </ContactForm>
    {/key}
  </div>
</main>
