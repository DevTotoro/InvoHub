<script lang="ts">
  import type { HTMLInputAttributes } from 'svelte/elements';

  interface Props extends Omit<HTMLInputAttributes, 'value'> {
    label: string;
    value?: string;
    /** Help text shown under the field */
    hint?: string;
    /** Shown instead of the hint, marks the field invalid and is announced to screen readers */
    error?: string;
  }

  let { label, value = $bindable(''), hint, error, id, class: className, ...rest }: Props = $props();

  const generatedId = $props.id();
  const inputId = $derived(id ?? generatedId);
  const descriptionId = $derived(`${inputId}-description`);
</script>

<div class={className}>
  <label for={inputId} class="block text-sm font-medium text-gray-900">{label}</label>
  <input
    id={inputId}
    bind:value
    aria-invalid={error ? true : undefined}
    aria-describedby={error || hint ? descriptionId : undefined}
    class={[
      'mt-1 block w-full border bg-white px-3 py-2 text-sm text-gray-900 placeholder:text-gray-400',
      'focus:ring-2 focus:outline-none disabled:cursor-not-allowed disabled:bg-gray-50',
      error
        ? 'border-red-500 focus:border-red-500 focus:ring-red-500/20'
        : 'border-gray-300 focus:border-taupe-500 focus:ring-taupe-500/20'
    ]}
    {...rest}
  />
  {#if error}
    <p id={descriptionId} class="mt-1 text-sm text-red-600" role="alert">{error}</p>
  {:else if hint}
    <p id={descriptionId} class="mt-1 text-sm text-gray-500">{hint}</p>
  {/if}
</div>
