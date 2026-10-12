<script lang="ts">
  import { m } from '#lib/paraglide/messages.js';
  import type { LayoutProps } from './$types';

  let { data, children }: LayoutProps = $props();
</script>

<!-- A suspended company can't be used: the API refuses everything in it, so no page of it is shown -->
{#if data.company.status === 'SUSPENDED'}
  <main class="container mx-auto px-4 py-16 text-center">
    <h2 class="text-xl font-semibold">{m.company_suspended_title()}</h2>
    <p class="mt-2 text-gray-600">{m.company_suspended_detail()}</p>
  </main>
{:else}
  {@render children()}
{/if}
