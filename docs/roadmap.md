# Roadmap

What's planned, what's decided, and what must happen before the first deployment. Update it as things are built or decisions change.

## Decisions

**Greece first.** The first market is Greece; the app starts in Greek and English, and Greek invoicing rules (below, under Invoices) shape the model.

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

**Company in the URL** (`/c/<public id>/…`). The URL only says which company is being viewed; the backend checks the user's role in it on every request.

**A company has** a legal name, country, tax ID, address and currency. Onboarding asks only for the country and the tax ID; the rest comes from the country's business registry where there is one (Greece: AADE), with manual entry as the fallback when the registry is unreachable or the country has none. The currency always comes from the country (JDK data). The tax ID is validated for the country (Greek ΑΦΜ: 9 digits with a check digit) but not unique across InvoHub, so nobody can block a business by registering its tax ID first.

**Country rules.** Each country has a rules module: how to check its tax ID, its registry lookup if any, the extra company details it needs (Greece: tax office, main activity) and later its e-invoicing. Countries without rules get manual entry and a basic check. Common fields are columns; country-specific details live in one JSON column checked by that country's rules, so adding a country never changes the companies table.

**Customers belong to a company**, not shared between companies. Invoices store the customer's details as they were when issued.

**Statuses:**
- Company: *Active*, *Read-only* (unpaid, after a grace period: can still view and export), *Suspended* (fraud or abuse, decided by InvoHub). Unpaid companies are never fully blocked.
- User *suspended*: InvoHub staff only, applies everywhere (`UserService.suspendUser`).

**Audit log** (built: `AuditLog`, table `audit.events`). Events are written in the same transaction as the change they describe (`AuditLog.record`; `recordStandalone` for events that belong to no other change, like sign-ins). The app's database login may only insert and read there, and a trigger refuses changes from every login. Recent months stay in Postgres for the activity page; the permanent copy goes to S3 with Object Lock (see the archive job below).

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

1. **Companies and roles** (`feat/companies`). Tables, the country-rules framework with Greek ΑΦΜ validation, "Set up your company" with manual entry (also the registry fallback), currency from the country, `me` returns the user's companies with their roles, company in the URL and a company switcher. Records `company.created`.
2. **AADE lookup** (`feat/aade-lookup`). Onboarding asks for country and tax ID, then fills in legal name, trade name, address, tax office and main activity from AADE's RgWsPublic2 service (SOAP, WS-Security username/password: InvoHub's own AADE access codes). Deactivated or unknown ΑΦΜs are refused; if AADE doesn't answer within a few seconds, the manual form is shown and the company is marked unverified, to be re-checked later. A fake registry with test ΑΦΜs for local development and tests.
3. **Translations.** Greek and English throughout, so every later page starts translated:
   - UI text through Paraglide (SvelteKit's recommended i18n), language chosen per user and stored on the user, defaulting to the browser's
   - Dates, numbers and amounts formatted for the language (`Intl`)
   - Error messages from the backend's problem `code`s, not its English `detail`
   - Emails (the sign-in link, later invitations) in the user's language
4. **Permission checks.** Resolve the user's role in the company on every request, together with the company status, in one central place. Tests proving nobody can reach another company's data.
5. **Members.** List members, change roles, remove people, leave a company.
6. **Invitations.** Invite an email to one or more companies, each with a role (inviters can only pick companies they own or administer). The emailed link signs the person in. Invitations expire after 7 days, can be resent and revoked, with a cap on pending invites and at most one resend per hour.
7. **Company settings.** Admins edit the company's details, bank details and logo; anyone can create more companies.
8. **Ownership transfer and company deletion.**

## Later

- **Audit archive job** (separate PR; design below) and monthly partitioning of the audit table, which also gives the job its by-time lookups (the existing indexes are by company, actor and target).
- **Activity page** for owners and admins; a "your sign-in history" page for users.
- **InvoHub staff tool:** suspend users, change company status.
- **Billing:** billing profiles, where one payer covers several companies with one bill (`companies.billing_profile_id`); unpaid leads to a grace period, then read-only.
- **Invoices:** numbering per company, immutable once issued, corrections through credit notes. Rules depend on the country (required fields, numbering, tamper-evidence, mandatory e-invoicing). For **Greece**, check the current rules when designing this:
  - the issuer's tax office (ΔΟΥ) and business activity on invoices: add them to the company then
  - reporting every invoice to AADE's myDATA
  - mandatory B2B e-invoicing, being phased in: each invoice is transmitted (to myDATA or through a certified provider) and gets back its MARK and a QR code, both printed on the invoice
- **Customers:** "copy from another company".
- **Event bus** (SQS, EventBridge, …) once something must react to events as they happen: notification emails, webhooks, analytics.
- **Valkey locally:** switch Docker and the tests from `redis:8` to the Valkey version used on AWS.

### Audit archive job

- Export closed **hourly windows**, each only once it is 15 minutes past its end. Transactions are capped at about a minute, so every event in the window has committed by then. Do not resume from a "last exported ID": IDs don't arrive in commit order, so events could be skipped.
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
