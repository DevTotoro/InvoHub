<script lang="ts">
  import { resolve } from '$app/paths';
  import { page } from '$app/state';
  import Link from '#lib/components/ui/Link.svelte';
  import { m } from '#lib/paraglide/messages.js';
  import type { LayoutProps } from './$types';

  let { data, children }: LayoutProps = $props();

  const params = $derived({ companyId: data.company.id });
  const tabs = $derived([
    { href: resolve('/(app)/c/[companyId]', params), label: m.company_nav_overview() },
    { href: resolve('/(app)/c/[companyId]/members', params), label: m.company_nav_team() }
  ]);
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
        {@const current = page.url.pathname === tab.href}
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
          </Link>
        </li>
      {/each}
    </ul>
  </nav>
  {@render children()}
{/if}
