import type { Fetch } from './client.ts';
import { graphql } from './graphql.ts';

export type ContactKind = 'BUSINESS' | 'PERSON';

/** Someone the company sells to or buys from */
export interface Contact {
  id: string;
  kind: ContactKind;
  name: string;
  /** ISO 3166-1 alpha-2 */
  country: string;
  /** Cleaned up and checked by the country's rules */
  taxId: string | null;
  email: string | null;
  phone: string | null;
  address: { street: string | null; city: string | null; postalCode: string | null };
  notes: string | null;
  /** ISO-8601, or null while in use */
  archivedAt: string | null;
}

/** A contact as typed; blank means none. The country defaults to the company's. */
export interface ContactInput {
  kind: ContactKind;
  name: string;
  country: string;
  taxId: string;
  email: string;
  phone: string;
  street: string;
  city: string;
  postalCode: string;
  notes: string;
}

export interface ContactPage {
  contacts: Contact[];
  /** All that match, on every page */
  total: number;
}

/** How many contacts a page of the list shows */
export const CONTACTS_PAGE_SIZE = 25;

export const CONTACT_FIELDS =
  'id kind name country taxId email phone address { street city postalCode } notes archivedAt';

/** By name; [query] matches anywhere in the name, tax ID or email */
export async function getContacts(
  companyId: string,
  options: { query?: string; archived?: boolean; offset?: number; limit?: number } = {},
  fetch?: Fetch
): Promise<ContactPage> {
  const { contacts } = await graphql<
    { contacts: ContactPage },
    { companyId: string; query?: string; archived?: boolean; offset?: number; limit?: number }
  >(
    `query Contacts($companyId: ID!, $query: String, $archived: Boolean, $offset: Int, $limit: Int) {
      contacts(companyId: $companyId, query: $query, archived: $archived, offset: $offset, limit: $limit) {
        total contacts { ${CONTACT_FIELDS} }
      }
    }`,
    { companyId, ...options },
    { fetch }
  );
  return contacts;
}

/** @throws GraphQLError `contact_not_found` */
export async function getContact(companyId: string, contactId: string, fetch?: Fetch): Promise<Contact> {
  const { contact } = await graphql<{ contact: Contact }, { companyId: string; contactId: string }>(
    `query Contact($companyId: ID!, $contactId: ID!) {
      contact(companyId: $companyId, contactId: $contactId) { ${CONTACT_FIELDS} }
    }`,
    { companyId, contactId },
    { fetch }
  );
  return contact;
}

/** @throws GraphQLError with `invalid_input` naming the field */
export async function createContact(companyId: string, input: ContactInput): Promise<Contact> {
  const { createContact } = await graphql<{ createContact: Contact }, { companyId: string; input: ContactInput }>(
    `mutation CreateContact($companyId: ID!, $input: ContactInput!) {
      createContact(companyId: $companyId, input: $input) { ${CONTACT_FIELDS} }
    }`,
    { companyId, input }
  );
  return createContact;
}

/** Replaces every detail. @throws GraphQLError with `invalid_input` naming the field */
export async function updateContact(companyId: string, contactId: string, input: ContactInput): Promise<Contact> {
  const { updateContact } = await graphql<
    { updateContact: Contact },
    { companyId: string; contactId: string; input: ContactInput }
  >(
    `mutation UpdateContact($companyId: ID!, $contactId: ID!, $input: ContactInput!) {
      updateContact(companyId: $companyId, contactId: $contactId, input: $input) { ${CONTACT_FIELDS} }
    }`,
    { companyId, contactId, input }
  );
  return updateContact;
}

export async function archiveContact(companyId: string, contactId: string): Promise<Contact> {
  const { archiveContact } = await graphql<{ archiveContact: Contact }, { companyId: string; contactId: string }>(
    `mutation ArchiveContact($companyId: ID!, $contactId: ID!) {
      archiveContact(companyId: $companyId, contactId: $contactId) { ${CONTACT_FIELDS} }
    }`,
    { companyId, contactId }
  );
  return archiveContact;
}

export async function restoreContact(companyId: string, contactId: string): Promise<Contact> {
  const { restoreContact } = await graphql<{ restoreContact: Contact }, { companyId: string; contactId: string }>(
    `mutation RestoreContact($companyId: ID!, $contactId: ID!) {
      restoreContact(companyId: $companyId, contactId: $contactId) { ${CONTACT_FIELDS} }
    }`,
    { companyId, contactId }
  );
  return restoreContact;
}
