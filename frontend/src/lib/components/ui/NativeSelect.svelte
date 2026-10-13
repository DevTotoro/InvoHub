<script lang="ts">
  import type { HTMLSelectAttributes } from 'svelte/elements';

  interface Option {
    value: string;
    label: string;
  }

  interface Props extends Omit<HTMLSelectAttributes, 'value'> {
    options: Option[];
    value?: string;
    /** Marks the field invalid */
    invalid?: boolean;
  }

  let { options, value = $bindable(''), invalid = false, class: className, ...rest }: Props = $props();
</script>

<!--
  The browser's own select, with its open list, but our own arrow and spacing: browsers draw theirs right against
  the edge. Labelled by the caller (aria-label, or a <label for>).
-->
<div class={['relative', className]}>
  <select
    bind:value
    aria-invalid={invalid ? true : undefined}
    class={[
      'block w-full appearance-none border bg-white py-2 pr-9 pl-3 text-sm text-gray-900',
      'focus:ring-2 focus:outline-none disabled:cursor-not-allowed disabled:bg-gray-50 disabled:text-gray-500',
      invalid
        ? 'border-red-500 focus:border-red-500 focus:ring-red-500/20'
        : 'border-gray-300 focus:border-taupe-500 focus:ring-taupe-500/20'
    ]}
    {...rest}
  >
    {#each options as option (option.value)}
      <option value={option.value}>{option.label}</option>
    {/each}
  </select>
  <svg
    class="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 text-gray-400"
    viewBox="0 0 20 20"
    fill="currentColor"
    aria-hidden="true"
  >
    <path
      fill-rule="evenodd"
      d="M5.22 8.22a.75.75 0 0 1 1.06 0L10 11.94l3.72-3.72a.75.75 0 1 1 1.06 1.06l-4.25 4.25a.75.75 0 0 1-1.06 0L5.22 9.28a.75.75 0 0 1 0-1.06Z"
      clip-rule="evenodd"
    />
  </svg>
</div>
