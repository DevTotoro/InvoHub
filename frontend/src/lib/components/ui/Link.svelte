<script lang="ts">
  import type { Snippet } from 'svelte';
  import type { HTMLAnchorAttributes } from 'svelte/elements';
  import { variantClasses, type Variant } from './styles.ts';

  interface Props extends HTMLAnchorAttributes {
    /** For pages in this app, pass `resolve(...)` from `$app/paths` */
    href: string;
    /** `plain` adds no styling beyond a focus ring, e.g. for a logo or an icon */
    variant?: Variant | 'plain';
    /** Opens in a new tab, without giving the other site access to this one */
    external?: boolean;
    children: Snippet;
  }

  let { href, variant = 'link', external = false, class: className, children, ...rest }: Props = $props();

  const classes = $derived(
    variant === 'plain'
      ? 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-taupe-600'
      : variantClasses(variant)
  );
</script>

<!-- Callers resolve internal hrefs (see the href prop), so the rule can't check them here -->
<!-- eslint-disable svelte/no-navigation-without-resolve -->
<a
  {href}
  class={[classes, className]}
  target={external ? '_blank' : undefined}
  rel={external ? 'noopener noreferrer' : undefined}
  {...rest}
>
  {@render children()}
</a>
<!-- eslint-enable svelte/no-navigation-without-resolve -->
