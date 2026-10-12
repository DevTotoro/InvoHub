<script lang="ts">
  import { resolve } from '$app/paths';
  import type { Company, UserCompany } from '#lib/api/companies.ts';
  import Button from '#lib/components/ui/Button.svelte';
  import Link from '#lib/components/ui/Link.svelte';
  import { m } from '#lib/paraglide/messages.js';

  interface Props {
    current: Company;
    companies: UserCompany[];
  }

  let { current, companies }: Props = $props();

  let open = $state(false);
  let container: HTMLElement | undefined = $state();
  const menuId = $props.id();

  function close() {
    open = false;
  }

  function closeOnOutsideClick(event: MouseEvent) {
    if (open && container && !container.contains(event.target as Node)) close();
  }
</script>

<svelte:window onclick={closeOnOutsideClick} onkeydown={(e) => e.key === 'Escape' && close()} />

<!-- Shrinks down to the name, so a long one is cut short instead of pushing into the rest of the header, but
     never below its arrow, so it can always be opened -->
<div class="relative min-w-10" bind:this={container}>
  <Button
    variant="ghost"
    class="max-w-full min-w-0"
    aria-expanded={open}
    aria-controls={menuId}
    onclick={() => (open = !open)}
  >
    <span class="max-w-48 truncate">{current.name}</span>
    <svg class="size-4 shrink-0 text-gray-400" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
      <path
        fill-rule="evenodd"
        d="M5.22 8.22a.75.75 0 0 1 1.06 0L10 11.94l3.72-3.72a.75.75 0 1 1 1.06 1.06l-4.25 4.25a.75.75 0 0 1-1.06 0L5.22 9.28a.75.75 0 0 1 0-1.06Z"
        clip-rule="evenodd"
      />
    </svg>
  </Button>

  {#if open}
    <div id={menuId} class="absolute left-0 z-10 mt-1 w-64 border border-gray-200 bg-white py-1 shadow-lg">
      <ul aria-label={m.company_your_companies()}>
        {#each companies as { company } (company.id)}
          <li>
            <Link
              href={resolve('/(app)/c/[companyId]', { companyId: company.id })}
              variant="plain"
              aria-current={company.id === current.id ? 'page' : undefined}
              onclick={close}
              class={[
                'block truncate px-3 py-2 text-sm hover:bg-gray-100',
                company.id === current.id ? 'font-semibold text-gray-900' : 'text-gray-700'
              ]}
            >
              {company.name}
            </Link>
          </li>
        {/each}
      </ul>
      <div class="mt-1 border-t border-gray-200 pt-1">
        <Link
          href={resolve('/(app)/onboarding')}
          variant="plain"
          onclick={close}
          class="block px-3 py-2 text-sm text-gray-700 hover:bg-gray-100"
        >
          {m.company_new()}
        </Link>
      </div>
    </div>
  {/if}
</div>
