// See https://svelte.dev/docs/kit/types#app.d.ts
// for information about these interfaces
import type { User } from '#lib/api/auth.ts';

declare global {
  namespace App {
    // interface Error {}
    // interface Locals {}
    interface PageData {
      /** The signed-in user, on pages under (app) */
      user?: User;
    }
    // interface PageState {}
    // interface Platform {}
  }
}

export {};
