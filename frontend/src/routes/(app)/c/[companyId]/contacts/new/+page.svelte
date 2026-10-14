<script lang="ts">
  import { goto } from '$app/navigation';
  import { resolve } from '$app/paths';
  import { createContact, type ContactInput } from '#lib/api/contacts.ts';
  import ContactForm from '#lib/components/ContactForm.svelte';
  import Link from '#lib/components/ui/Link.svelte';
  import { m } from '#lib/paraglide/messages.js';
  import type { PageProps } from './$types';

  let { data }: PageProps = $props();

  const params = $derived({ companyId: data.company.id });
  const blank = $derived<ContactInput>({
    kind: 'BUSINESS',
    name: '',
    country: data.company.country,
    taxId: '',
    email: '',
    phone: '',
    street: '',
    city: '',
    postalCode: '',
    notes: ''
  });

  async function save(input: ContactInput) {
    const contact = await createContact(data.company.id, input);
    await goto(resolve('/(app)/c/[companyId]/contacts/[contactId]', { ...params, contactId: contact.id }));
  }
</script>

<svelte:head>
  <title>{m.contacts_new()} · {data.company.name} · InvoHub</title>
</svelte:head>

<main class="container mx-auto px-4 py-8">
  <Link href={resolve('/(app)/c/[companyId]/contacts', params)} class="text-sm">{m.contact_back()}</Link>
  <h2 class="mt-2 mb-6 text-xl font-semibold">{m.contacts_new()}</h2>
  <ContactForm
    countries={data.countries}
    initial={blank}
    submitLabel={m.contact_add()}
    busyLabel={m.contact_adding()}
    onsave={save}
  />
</main>
