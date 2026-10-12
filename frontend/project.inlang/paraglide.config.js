import { defineConfig } from '@inlang/paraglide-js';

// Read by both the Vite plugin and `pnpm i18n`, so the dev server, builds and type checks compile alike
export default defineConfig({
  outdir: './src/lib/paraglide',
  // The language picked in InvoHub (kept in a cookie, and on the user once signed in), else the browser's.
  // Not in the URL: pages render in the browser and aren't indexed, so localized URLs would add nothing.
  strategy: ['cookie', 'preferredLanguage', 'baseLocale'],
  emitTsDeclarations: true
});
