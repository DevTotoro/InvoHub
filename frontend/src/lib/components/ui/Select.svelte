<script lang="ts">
  import type { HTMLSelectAttributes } from 'svelte/elements';

  interface Option {
    value: string;
    label: string;
  }

  interface Props extends Omit<HTMLSelectAttributes, 'value'> {
    label: string;
    options: Option[];
    value?: string;
    /** Help text shown under the field */
    hint?: string;
    /** Shown instead of the hint, marks the field invalid and is announced to screen readers */
    error?: string;
  }

  let { label, options, value = $bindable(''), hint, error, id, class: className, ...rest }: Props = $props();

  const generatedId = $props.id();
  const selectId = $derived(id ?? generatedId);
  const descriptionId = $derived(`${selectId}-description`);
</script>

<div class={className}>
  <label for={selectId} class="block text-sm font-medium text-gray-900">{label}</label>
  <select
    id={selectId}
    bind:value
    aria-invalid={error ? true : undefined}
    aria-describedby={error || hint ? descriptionId : undefined}
    class={[
      'mt-1 block w-full border bg-white px-3 py-2 text-sm text-gray-900',
      'focus:ring-2 focus:outline-none disabled:cursor-not-allowed disabled:bg-gray-50',
      error
        ? 'border-red-500 focus:border-red-500 focus:ring-red-500/20'
        : 'border-gray-300 focus:border-taupe-500 focus:ring-taupe-500/20'
    ]}
    {...rest}
  >
    {#each options as option (option.value)}
      <option value={option.value}>{option.label}</option>
    {/each}
  </select>
  {#if error}
    <p id={descriptionId} class="mt-1 text-sm text-red-600" role="alert">{error}</p>
  {:else if hint}
    <p id={descriptionId} class="mt-1 text-sm text-gray-500">{hint}</p>
  {/if}
</div>
