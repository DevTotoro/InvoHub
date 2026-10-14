<script lang="ts">
  import { resolve } from '$app/paths';
  import { page } from '$app/state';
  import { invalidateAll } from '$app/navigation';
  import { reopenCompany } from '#lib/api/ownership.ts';
  import { setupPending } from '#lib/api/settings.ts';
  import Button from '#lib/components/ui/Button.svelte';
  import { errorMessage } from '#lib/i18n/errors.ts';
  import Link from '#lib/components/ui/Link.svelte';
  import { m } from '#lib/paraglide/messages.js';
  import type { LayoutProps } from './$types';

  let { data, children }: LayoutProps = $props();

  const params = $derived({ companyId: data.company.id });
  const tabs = $derived([
    { href: resolve('/(app)/c/[companyId]', params), label: m.company_nav_overview() },
    { href: resolve('/(app)/c/[companyId]/contacts', params), label: m.company_nav_contacts() },
    { href: resolve('/(app)/c/[companyId]/members', params), label: m.company_nav_team() },
    ...(data.settings
      ? [
          {
            href: resolve('/(app)/c/[companyId]/settings', params),
            label: m.company_nav_settings(),
            // A dot while invoicing setup has steps left to do
            attention: !data.company.closedAt && setupPending(data.settings.invoicingSetup)
          }
        ]
      : [])
  ]);

  let reopening = $state(false);
  let reopenError: string | undefined = $state();

  async function reopen() {
    reopening = true;
    reopenError = undefined;
    try {
      await reopenCompany(data.company.id);
      await invalidateAll();
    } catch (e) {
      reopenError = errorMessage(e);
    } finally {
      reopening = false;
    }
  }
</script>

<!-- A suspended company can't be used: the API refuses everything in it, so no page of it is shown -->
{#if data.company.status === 'SUSPENDED'}
  <main class="container mx-auto px-4 py-16 text-center">
    <h2 class="text-xl font-semibold">{m.company_suspended_title()}</h2>
    <p class="mt-2 text-gray-600">{m.company_suspended_detail()}</p>
  </main>
{:else}
  <nav aria-label={m.company_nav()} class="container mx-auto border-b border-gray-200 px-4">
    <ul class="flex gap-4">
      {#each tabs as tab (tab.href)}
        <!-- A tab stays current on the pages under it (a contact under Contacts); Overview only on itself -->
        {@const current =
          page.url.pathname === tab.href || (tab !== tabs[0] && page.url.pathname.startsWith(`${tab.href}/`))}
        <li>
          <Link
            href={tab.href}
            variant="plain"
            aria-current={current ? 'page' : undefined}
            class={[
              '-mb-px block border-b-2 py-2 text-sm',
              current
                ? 'border-gray-900 font-medium text-gray-900'
                : 'border-transparent text-gray-600 hover:text-gray-900'
            ]}
          >
            {tab.label}
            {#if 'attention' in tab && tab.attention}
              <span class="ml-1 inline-block size-1.5 -translate-y-0.5 rounded-full bg-amber-500" aria-hidden="true"
              ></span>
              <span class="sr-only">({m.settings_needed()})</span>
            {/if}
          </Link>
        </li>
      {/each}
    </ul>
  </nav>
  {#if data.company.closedAt}
    <div class="border-b border-gray-200 bg-gray-50">
      <div class="container mx-auto flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-2" role="status">
        <p class="text-sm text-gray-700">{m.company_closed_banner()}</p>
        {#if data.role === 'OWNER'}
          <Button variant="secondary" loading={reopening} onclick={reopen}>{m.company_reopen()}</Button>
        {/if}
        {#if reopenError}
          <p class="text-sm text-red-600" role="alert">{reopenError}</p>
        {/if}
      </div>
    </div>
  {/if}
  {@render children()}
{/if}
