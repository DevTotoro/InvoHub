<script lang="ts">
  import type { LayoutProps } from './$types';

  let { children }: LayoutProps = $props();
</script>

<main class="flex justify-center px-4 py-16">
  <div class="w-full max-w-sm">
    {@render children()}
  </div>
</main>
