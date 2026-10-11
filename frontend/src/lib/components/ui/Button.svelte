<script lang="ts">
  import type { Snippet } from 'svelte';
  import type { HTMLButtonAttributes } from 'svelte/elements';
  import { variantClasses, type Variant } from './styles.ts';

  interface Props extends HTMLButtonAttributes {
    variant?: Variant;
    /** Shows a spinner and disables the button while an action is running */
    loading?: boolean;
    fullWidth?: boolean;
    children: Snippet;
  }

  let {
    variant = 'primary',
    loading = false,
    fullWidth = false,
    // Not the HTML default ("submit"), so a button inside a form never submits it by accident
    type = 'button',
    disabled,
    class: className,
    children,
    ...rest
  }: Props = $props();
</script>

<button
  {type}
  disabled={disabled || loading}
  aria-busy={loading || undefined}
  class={[variantClasses(variant), fullWidth && 'w-full', className]}
  {...rest}
>
  {#if loading}
    <svg class="size-4 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" class="opacity-25" />
      <path d="M22 12a10 10 0 0 0-10-10" stroke="currentColor" stroke-width="4" stroke-linecap="round" />
    </svg>
  {/if}
  {@render children()}
</button>
