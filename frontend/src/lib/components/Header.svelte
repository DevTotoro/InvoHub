<script lang="ts">
  import { resolve } from '$app/paths';
  import github from '#lib/assets/github.svg';
</script>

<header class="w-full">
  <div class="container mx-auto flex h-16 items-center justify-between space-x-2 px-4">
    <h1 class="text-2xl font-bold"><a href={resolve('/')}>InvoHub</a></h1>

    <a href="https://github.com/DevTotoro/InvoHub" target="_blank" rel="noopener noreferrer">
      <img src={github} alt="GitHub" class="h-6 w-6" />
    </a>
  </div>
</header>
