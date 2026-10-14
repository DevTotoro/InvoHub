<script lang="ts">
  import { resolve } from '$app/paths';
  import { CONTACTS_PAGE_SIZE as PAGE_SIZE } from '#lib/api/contacts.ts';
  import { canEdit } from '#lib/company/permissions.ts';
  import Link from '#lib/components/ui/Link.svelte';
  import { m } from '#lib/paraglide/messages.js';
  import type { PageProps } from './$types';

  let { data }: PageProps = $props();

  const params = $derived({ companyId: data.company.id });
  const editable = $derived(canEdit(data.role, data.company));
  const from = $derived((data.page - 1) * PAGE_SIZE + 1);
  const to = $derived(Math.min(data.page * PAGE_SIZE, data.total));

  const listPath = $derived(resolve('/(app)/c/[companyId]/contacts', params));

  /** The list's search string with some of its settings changed; any change but the page starts from page 1 */
  function listSearch(change: { q?: string; archived?: boolean; page?: number }) {
    const q = change.q ?? data.query;
    const archived = change.archived ?? data.archived;
    const entries: [string, string][] = [];
    if (q) entries.push(['q', q]);
    if (archived) entries.push(['archived', '1']);
    if (change.page && change.page > 1) entries.push(['page', String(change.page)]);
    return entries.length > 0 ? `?${new URLSearchParams(entries)}` : '';
  }

  let searchTimer: ReturnType<typeof setTimeout> | undefined;

  /** Searches as the user types, once they pause (the form itself works without this, on Enter) */
  function search(event: Event & { currentTarget: HTMLInputElement }) {
    const form = event.currentTarget.form!;
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => form.requestSubmit(), 300);
  }

  const empty = $derived(
    data.query ? m.contacts_no_match() : data.archived ? m.contacts_archived_none() : m.contacts_none()
  );
</script>

<svelte:head>
  <title>{m.contacts_title()} · {data.company.name} · InvoHub</title>
</svelte:head>

<main class="container mx-auto px-4 py-8">
  <div class="flex flex-wrap items-center justify-between gap-4">
    <h2 id="contacts-title" class="text-xl font-semibold">{m.contacts_title()}</h2>
    {#if editable}
      <Link href={resolve('/(app)/c/[companyId]/contacts/new', params)} variant="primary">{m.contacts_new()}</Link>
    {/if}
  </div>

  <div class="mt-4 flex flex-wrap items-center gap-4">
    <!-- A GET form: the search lands in the address, kept on reload and back; one history entry, focus kept -->
    <form
      method="GET"
      action={listPath}
      role="search"
      class="w-full max-w-sm"
      data-sveltekit-replacestate
      data-sveltekit-reset="false"
    >
      <input
        type="search"
        name="q"
        aria-label={m.contacts_search()}
        placeholder={m.contacts_search()}
        value={data.query}
        oninput={search}
        class="w-full border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 placeholder:text-gray-400 focus:border-taupe-500 focus:ring-2 focus:ring-taupe-500/20 focus:outline-none"
      />
      {#if data.archived}<input type="hidden" name="archived" value="1" />{/if}
    </form>
    <nav aria-label={m.contacts_title()} class="flex gap-3 text-sm">
      {#each [{ archived: false, label: m.contacts_in_use() }, { archived: true, label: m.contacts_archived() }] as view (view.archived)}
        <Link
          href={`${listPath}${listSearch({ archived: view.archived })}`}
          variant="plain"
          aria-current={data.archived === view.archived ? 'page' : undefined}
          class={data.archived === view.archived ? 'font-medium text-gray-900' : 'text-gray-600 hover:text-gray-900'}
        >
          {view.label}
        </Link>
      {/each}
    </nav>
  </div>

  {#if data.contacts.length === 0}
    <p class="mt-6 text-sm text-gray-600">{empty}</p>
  {:else}
    <ul aria-labelledby="contacts-title" class="mt-4 divide-y divide-gray-200 border-y border-gray-200">
      {#each data.contacts as contact (contact.id)}
        <li>
          <Link
            href={resolve('/(app)/c/[companyId]/contacts/[contactId]', { ...params, contactId: contact.id })}
            variant="plain"
            class="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 py-3 hover:bg-gray-50"
          >
            <span class="min-w-0 truncate text-sm font-medium text-gray-900">{contact.name}</span>
            <span class="text-sm text-gray-500">
              {[contact.taxId, contact.email, contact.address.city].filter(Boolean).join(' · ')}
            </span>
          </Link>
        </li>
      {/each}
    </ul>

    {#if data.total > PAGE_SIZE}
      <div class="mt-4 flex items-center gap-4 text-sm text-gray-600">
        <span>{m.contacts_range({ from, to, total: data.total })}</span>
        {#if data.page > 1}
          <Link href={`${listPath}${listSearch({ page: data.page - 1 })}`}>{m.contacts_previous()}</Link>
        {/if}
        {#if to < data.total}
          <Link href={`${listPath}${listSearch({ page: data.page + 1 })}`}>{m.contacts_next()}</Link>
        {/if}
      </div>
    {/if}
  {/if}
</main>
