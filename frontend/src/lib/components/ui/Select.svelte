<script lang="ts">
  import type { HTMLSelectAttributes } from 'svelte/elements';
  import NativeSelect from './NativeSelect.svelte';

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
  <NativeSelect
    id={selectId}
    bind:value
    {options}
    invalid={!!error}
    aria-describedby={error || hint ? descriptionId : undefined}
    class="mt-1"
    {...rest}
  />
  {#if error}
    <p id={descriptionId} class="mt-1 text-sm text-red-600" role="alert">{error}</p>
  {:else if hint}
    <p id={descriptionId} class="mt-1 text-sm text-gray-500">{hint}</p>
  {/if}
</div>
