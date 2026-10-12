# Roadmap

What's planned, what's decided, and what must happen before the first deployment. Update it as things are built or decisions change.

## Decisions

**Greece first.** The first market is Greece; the app starts in Greek and English, and Greek invoicing rules (below, under Invoices) shape the model.

**Languages: Greek and English** (built). Each user has a language, for the app and their emails. Signed out, the app uses the one picked in the header (kept in a cookie), else the browser's; signing in switches to the account's, which wins. A first sign-in creates the account in the language in use. Not in the URL: pages render in the browser and aren't indexed. App texts are in `frontend/messages/`, email texts in `backend/src/main/resources/i18n/`; tests fail when a language is missing a text. Errors are shown by the API's problem `code`, never its English `detail`.

**Companies (no account layer).** Each company stands alone, like a Xero organisation. A user can belong to many companies, with a role in each. If people with several companies want a single bill, that is a billing feature (billing profiles, below), not a grouping of companies.

**Roles, per company:**

| | Owner | Admin | Member | Viewer |
|---|---|---|---|---|
| See the company | ✓ | ✓ | ✓ | ✓ |
| Create and edit invoices, customers, … | ✓ | ✓ | ✓ | |
| Company settings | ✓ | ✓ | | |
| Invite, remove, change roles | ✓ | ✓ (never the owner) | | |
| Transfer ownership, delete the company, billing | ✓ | | | |

- Exactly one owner per company, stored on the company (`owner_user_id`). Anyone can create a company and becomes its owner.
- Removing someone deletes their membership; the user remains, so their past work keeps their name. The history lives in the audit log.
- Members (built): everyone in a company sees its people. The owner and admins change roles and remove people, never the owner, and nobody changes or removes themselves (they leave instead). The owner role is never given from the role menu, and the owner can't leave: ownership moves only through a transfer. A removed member loses access with their next request.

**Company in the URL** (`/c/<public id>/…`). The URL only says which company is being viewed; the backend checks the user's role in it on every request.

**Permission checks** (built). `CompanyAccessService` is the one place that works out a user's role in a company; `CompanyPermission` says which roles may do what (the table above) and what each company status allows. Every company-scoped operation names the permission it needs. Rules:
- No access is reported as `not_found`, exactly like a company that doesn't exist, so nobody can find out which companies exist. A role that doesn't allow something is `forbidden`.
- Company-scoped GraphQL root fields take the company as `companyId` (or are `company(id)`) and start with `CompanyScope`, which also logs the company as `companyId`. `CompanyIsolationTest` runs each one as an outsider and fails for a new field it has no case for.

**Creating a company asks for the minimum: a name and a country.** Any country is allowed. The currency comes from the country (JDK data). Everything else is only needed to issue invoices, so it is asked for just in time, in the company's invoicing setup checklist: the tax ID, legal name, address and the country's own details. A company can do everything except issue invoices before that; drafts are fine. The country can change until the first invoice is issued, then it is fixed.

**Country rules.** Each country InvoHub supports has a rules module: how to check its tax ID, its business registry if any, and its invoicing setup checklist (later also e-invoicing, tax rates, numbering). A country is supported exactly when it has a module. Companies in any other country get a checklist that can't be completed, "Invoicing in <country> isn't available yet", and onboarding says so up front; once we add the country's rules, those companies see real steps. Common fields are columns; country-specific details live in one JSON column checked by that country's rules, so adding a country never changes the companies table.

**Greece's invoicing setup checklist:**
1. Tax ID (ΑΦΜ): 9 digits with a check digit. Not unique across InvoHub, so nobody can block a business by registering its tax ID first.
2. Business details: legal name, address, tax office (ΔΟΥ), main activity (ΚΑΔ). Entered by hand for now; later filled in from AADE's registry (see Later).
3. Connect myDATA with the company's own user ID and key. This also verifies the ΑΦΜ (to confirm against AADE's documentation when built).

**Customers belong to a company**, not shared between companies. Invoices store the customer's details as they were when issued.

**Statuses:**
- Company (`companies.status`, built): *Active*, *Read-only* (unpaid, after a grace period: can still view and export, and the owner can pay), *Suspended* (fraud or abuse, decided by InvoHub: nobody can use it, not even the owner). Unpaid companies are never fully blocked.
- User *suspended*: InvoHub staff only, applies everywhere (`UserService.suspendUser`).

**Audit log** (built: `AuditLog`, table `audit.events`). Events are written in the same transaction as the change they describe (`AuditLog.record`; `recordStandalone` for events that belong to no other change, like sign-ins). The app's database login may only insert and read there, and a trigger refuses changes from every login. Recent months stay in Postgres for the activity page; the permanent copy goes to S3 with Object Lock (see the archive job below).

**Public IDs outside the app.** Internal (numeric) IDs are only used inside the app and in Postgres. Everything stored or shown elsewhere uses public IDs: URLs and the API, Redis keys, the session, logs, exports.

**Two database logins.** Migrations run as the schema owner (`spring.flyway.user`); the app connects with its own login (`spring.datasource.username`), which can read and write data but never change the schema. Grants are in `V1__initial_setup.sql`.

Events to record (the `user.*` ones are recorded already):

| | |
|---|---|
| Users | `user.created`, `user.signed_in`, `user.signed_out`, `user.sign_in_refused`, `user.suspended`, `user.unsuspended` |
| Companies | `company.created`, `company.updated` (before/after), `company.status_changed`, `company.ownership_transferred` |
| Members | `member.added`, `member.role_changed`, `member.removed`, `member.left` |
| Invitations | `invitation.sent`, `invitation.resent`, `invitation.revoked`, `invitation.accepted` |
| Later | `invoice.issued`, `invoice.voided`, `credit_note.issued`, `export.downloaded` |

Reads and page views are not recorded.

**Earlier decisions (sign-in):**
- No new sign-in link while one is outstanding (one hour). This is deliberate: it stops inbox flooding.
- Per-IP rate limiting is handled by the CDN, not the app.
- Sessions end after 2 hours of inactivity and at most 7 days after sign-in.
- Suspending a user must go through `UserService.suspendUser`, which ends their sessions. Changing the database directly does not.

## Next up

Built so far: companies and roles, translations, permission checks, and members. Every new page and email is written in both languages, and every company-scoped operation goes through the permission checks.

1. **Invitations.** Invite an email to one or more companies, each with a role (inviters can only pick companies they own or administer). The emailed link signs the person in. Invitations expire after 7 days, can be resent and revoked, with a cap on pending invites and at most one resend per hour.
2. **Company settings.** Admins edit the company's details (including the tax ID, legal name, address and country details from the checklist), bank details and logo; anyone can create more companies.
3. **Ownership transfer and company deletion.**

## Later

- **Audit archive job** (separate PR; design below) and monthly partitioning of the audit table, which also gives the job its by-time lookups (the existing indexes are by company, actor and target).
- **AADE registry lookup:** fill in a Greek company's legal name, trade name, address, tax office and activities from its ΑΦΜ through AADE's RgWsPublic2 service (SOAP, WS-Security username/password). Needs AADE access codes: InvoHub's own, which need an ΑΦΜ of its own, or optionally the company's. Fall back to manual entry when AADE doesn't answer.
- **Activity page** for owners and admins; a "your sign-in history" page for users.
- **InvoHub staff tool:** suspend users, change company status.
- **Billing:** billing profiles, where one payer covers several companies with one bill (`companies.billing_profile_id`); unpaid leads to a grace period, then read-only.
- **Invoices:** the invoicing setup checklist screens and the block on issuing until it is complete; numbering per company, immutable once issued, corrections through credit notes. Rules depend on the country (required fields, numbering, tamper-evidence, mandatory e-invoicing). For **Greece**, check the current rules when designing this:
  - the issuer's tax office (ΔΟΥ) and business activity on invoices: add them to the company then
  - reporting every invoice to AADE's myDATA
  - mandatory B2B e-invoicing, being phased in: each invoice is transmitted (to myDATA or through a certified provider) and gets back its MARK and a QR code, both printed on the invoice
- **Customers:** "copy from another company".
- **Event bus** (SQS, EventBridge, …) once something must react to events as they happen: notification emails, webhooks, analytics.
- **Valkey locally:** switch Docker and the tests from `redis:8` to the Valkey version used on AWS.

### Audit archive job

- Export closed **hourly windows**, each only once it is 15 minutes past its end. Transactions are capped at about a minute, so every event in the window has committed by then. Do not resume from a "last exported ID": IDs don't arrive in commit order, so events could be skipped.
- Events are exported with public IDs (actor, company, target), like everything stored outside the app; only Postgres uses internal IDs.
- Each window becomes one S3 object with a fixed name (`audit/YYYY/MM/DD/HH.jsonl.gz`), uploaded with "create only if absent", in a bucket with Object Lock in compliance mode.
- Progress is recorded in `audit.export_windows`: window, object key, event count, checksum. Each run exports the oldest finished window without a row, so the job resumes by itself.
- Crash after upload but before the row: the next run finds the object, checks its checksum, and records the row. Retry with backoff; stop at the first failing window, never skip one.
- Only one runner at a time: a Postgres advisory lock, or a standalone scheduled task.
- Alarm when the oldest unexported window is more than a few hours old.
- Removing months from Postgres is a separate step: only when every hour is exported, counts match a recount, and the month is older than the activity page's window (about 12 months). Otherwise, alert.

## Before the first deployment

- Serve the frontend and `/api` from the same site (e.g. route `/api` at the CDN or on Vercel); HTTPS only, the session cookie is `Secure`.
- Create the app's database login in the database setup, with no rights of its own (the migrations grant them). Like `backend/docker/postgres-init/create-app-login.sh` does locally.
- Environment: `SPRING_PROFILES_ACTIVE=prod`, `INVOHUB_AUTH_FRONTEND_URL` (required), `SPRING_DATASOURCE_URL` with `SPRING_DATASOURCE_USERNAME`/`_PASSWORD` set to the app's login, `SPRING_FLYWAY_USER`/`_PASSWORD` set to the schema owner, `SPRING_DATA_REDIS_*` with `SPRING_DATA_REDIS_SSL_ENABLED=true` (ElastiCache Serverless requires TLS), `SPRING_MAIL_*`, `INVOHUB_MAIL_FROM`.
- Run once against a real ElastiCache Serverless cache: its sharding can't be reproduced locally.
- Per-IP rate limiting at the CDN.
- Audit table partitioning, the S3 bucket with Object Lock, and the archive job.
- From then on, schema changes go into new Flyway migrations instead of editing `V1__initial_setup.sql`.
