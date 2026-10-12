// See https://svelte.dev/docs/kit/types#app.d.ts
// for information about these interfaces
import type { User } from '#lib/api/auth.ts';
import type { Company, CompanyRole } from '#lib/api/companies.ts';

declare global {
  namespace App {
    // interface Error {}
    // interface Locals {}
    interface PageData {
      /** The signed-in user, on pages under (app) */
      user?: User;
      /** The company being worked in, on pages under /c/[companyId], and the user's role in it */
      company?: Company;
      role?: CompanyRole;
    }
    // interface PageState {}
    // interface Platform {}
  }
}

export {};
