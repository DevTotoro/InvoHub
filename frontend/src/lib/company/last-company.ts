import type { UserCompany } from '#lib/api/companies.ts';

const STORAGE_KEY = 'invohub:last-company';

/** Remembers the company the user is working in, so `/` can take them back to it. Per browser. */
export function rememberCompany(id: string): void {
  try {
    localStorage.setItem(STORAGE_KEY, id);
  } catch {
    // Storage unavailable: `/` falls back to the first company
  }
}

/** The company to open: the last one used, if the user still has access to it, otherwise the first. */
export function pickCompany(companies: UserCompany[]): UserCompany | undefined {
  let lastId: string | null = null;
  try {
    lastId = localStorage.getItem(STORAGE_KEY);
  } catch {
    // Storage unavailable: use the first company
  }
  return companies.find((c) => c.company.id === lastId) ?? companies[0];
}
