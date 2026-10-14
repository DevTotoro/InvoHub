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
| Transfer ownership, close the company, billing | ✓ | | | |

- Exactly one owner per company, stored on the company (`owner_user_id`). Anyone can create a company and becomes its owner.
- Removing someone deletes their membership; the user remains, so their past work keeps their name. The history lives in the audit log.
- Invitations (built): the owner or an admin invites an email address to one or more of their companies, each with a role. The invitation belongs to the address: whoever signs in with it accepts or declines on their Invitations page. Invitations stay open 7 days; once one has expired, been declined or been revoked, the person can be invited again.
  - Email: one per invite, sent as part of it (if it can't be sent, nothing is saved). Its button is a normal one-hour sign-in link that opens the Invitations page. In the person's language if they have an account, otherwise English (the inviter's may not be theirs).
  - Limits, as Redis counters that last a day: 100 invitations per inviter (creating companies doesn't reset it) and 10 invitation emails per address, whoever sends them (so nobody is flooded). Past either, inviting is refused with the reason. The email has no free text from the inviter; the company name is escaped.
- Sign-in links (built): a random token, stored only as a hash, that works once for an hour; `redirectTo` in the link says where it leads once signed in (an invitation's opens the Invitations page). A link that no longer works says "This link has expired" with Sign in, which keeps `redirectTo`. Every sign-in page (login, verify) sends someone already signed in on to `redirectTo`, or home; a link opened then isn't used. Emails are HTML with a button like the app's, the link written out below it, and a plain-text version.
- Members (built): everyone in a company sees its people. The owner and admins change roles and remove people, never the owner, and nobody changes or removes themselves (they leave instead). The owner role is never given from the role menu, and the owner can't leave: ownership moves only through a transfer. A removed member loses access with their next request.

**Company in the URL** (`/c/<public id>/…`). The URL only says which company is being viewed; the backend checks the user's role in it on every request.

**Permission checks** (built). `CompanyAccessService` is the one place that works out a user's role in a company; `CompanyPermission` says which roles may do what (the table above) and what each company status allows. Every company-scoped operation names the permission it needs. Rules:
- No access is reported as `not_found`, exactly like a company that doesn't exist, so nobody can find out which companies exist. A role that doesn't allow something is `forbidden`.
- Company-scoped GraphQL root fields take the company as `companyId` (or are `company(id)`) and start with `CompanyScope`, which also logs the company as `companyId`. `CompanyIsolationTest` runs each one as an outsider and fails for a new field it has no case for.

**Creating a company asks for the minimum: a name and a country.** Any country is allowed. The currency comes from the country (JDK data). Everything else is only needed to issue invoices, so it is asked for just in time, in the company's invoicing setup checklist: the tax ID, legal name, address and the country's own details. A company can do everything except issue invoices before that; drafts are fine. The country can change until the first invoice is issued, then it is fixed.

**Country rules.** Each country InvoHub supports has a rules module: how to check its tax ID, its business registry if any, and its invoicing setup checklist (later also e-invoicing, tax rates, numbering). A country is supported exactly when it has a module. Companies in any other country get a checklist that can't be completed, "Invoicing in <country> isn't available yet", and onboarding says so up front; once we add the country's rules, those companies see real steps. Common fields are columns; country-specific details live in one JSON column checked by that country's rules, so adding a country never changes the companies table.

**Greece's invoicing setup checklist:**
1. Tax ID (ΑΦΜ): 9 digits with a check digit. Not unique across InvoHub, so nobody can block a business by registering its tax ID first.
2. Business details: legal name, address, tax office (ΔΟΥ), main activity (ΚΑΔ, 2 to 8 digits). Entered by hand for now, the tax office as free text: there is no dependable official list, and offices are being merged in 2026. Later filled in from AADE's registry (see Next up).
3. Connect myDATA with the company's own user ID and key. This also verifies the ΑΦΜ (to confirm against AADE's documentation when built). Shown as "Coming soon" until then, so it doesn't count as left to do.

**Company settings** (built). A Settings tab for the owner and admins, also while the company is read-only (they can look, not change). Sections:
- Company: name and country. Moving the country clears the tax ID and the country's own details, after asking; the currency follows the country.
- Business details: legal name, address, tax ID and the country's own details, checked by the country's rules.
- Bank accounts: up to 10, each a bank name and an IBAN (check digits verified, unique within the company).
- Logo, optional: PNG, JPEG or WebP (recognized by the file's contents; SVG is refused, as it can carry scripts), up to 1 MB. Public, as it's printed on every invoice (see Files in S3).

While steps of the invoicing setup checklist that can be done are left, the owner and admins see the checklist on Overview and a dot on the Settings tab; each section says whether it's done, needed or optional. Every change is recorded as `company.updated` with each changed field before and after.

**Files in S3.** Logos now, and later invoice PDFs, attachments and exports. Locally and in tests, an S3-compatible server in Docker: [Silo](https://github.com/pgsty/silo), a MinIO fork. It only runs alongside the app, so its AGPL licence doesn't reach InvoHub's code. In production, a private bucket the app reaches with its IAM role.
- Uploads go from the browser straight to the bucket, never through the app. The app signs a URL for one file under `pending/`: one key, one content type and one exact size, for 5 minutes, and S3 refuses anything else. The browser then names the upload, and the app checks what arrived (the real size, and the file's first bytes against the type) before using it; a file that fails is deleted. The key is built from the company, so nobody can name another company's upload. Unused uploads under `pending/` expire after a day.
- Public files (logos) are served by CloudFront from `logos/`, the only part it may read. Each logo has its own key, so it is cached for good (`immutable`) and a new logo is simply a new URL: nothing is ever invalidated. A removed logo stays in CloudFront's cache until it expires, unlinked; one that must go at once is invalidated by hand. Being on another domain than the app also means an uploaded file can never run as part of InvoHub.
- Private files (later: attachments, exports) are never public: the app checks access, then hands out a short-lived signed URL to download.
- Invoice PDFs are made on the server, which reads the logo from S3 directly.
- The old logo, or a checked upload, is only deleted once the change is saved, so a failed save never leaves the company pointing at a missing file.

**Ownership transfer** (built). The owner offers ownership to someone already in the company, who accepts or declines; ownership carries billing and closing, so nobody gets it without saying yes. They get an email and see it on their Invitations page; it stays open 7 days, one at a time, and the owner can cancel it. It is cancelled if the person leaves or is removed. Once accepted, the old owner stays on as an admin. Possible while the company is read-only (it can be handed to someone who'll pay) or closed, not while suspended.

**Closing, not deleting** (built). The owner closes a company; nothing is deleted. It becomes read-only for everyone in it, who can still look at and export everything, and its open invitations are revoked. It moves under "Closed" in the company switcher, and `/` prefers a company in use. The owner can reopen it at any time.
- How long a business keeps its records is its own decision (and its legal duty: in Greece at least 5 years from the end of the tax year, longer in some cases; to confirm with an accountant). Under GDPR the business is the controller of its invoices and customers, InvoHub the processor, so InvoHub never deletes a company's data on its own schedule.
- Permanent deletion will be the owner's choice, of a closed company, with a clear warning about keeping records, once there are invoices to warn about (see Later). The terms of service say this (see Before the first deployment).

**Contacts belong to a company**, not shared between companies: one list for customers and suppliers alike (a contact can be both). Invoices store the contact's details as they were when issued.

**Products and stock are the same in every country.** Items have a name, code, unit, price and tax rate; which tax rates exist comes from the country's rules, and country-specific extras (Greece: myDATA defaults) are optional fields those rules add, like the company's own country details. Invoice lines copy an item's values, so changing an item never changes an issued invoice.

**Stock is a ledger of movements**, never an edited count: issuing a sales invoice moves items out, a credit note back in, a purchase invoice in, a manual adjustment in or out with a reason. Each movement records the item, the quantity, the date it happened (the document's date, not when it was typed in) and the document it came from. Quantities only for now; stock may go below zero, with a warning. So that stock value and profit can be worked out later (average cost, the usual choice for small businesses), the costs are recorded from the start: purchase lines keep their unit cost after discount in the company's currency (and the original currency and amount), manual "stock in" adjustments take an optional cost (opening stock), and returns to suppliers are documents of their own, linked to the purchase. Purchase invoices, and later costs and margins, are for owners and admins only; quantities for everyone.

**Statuses:**
- Company (`companies.status`, built): *Active*, *Read-only* (unpaid, after a grace period: can still view and export, and the owner can pay), *Suspended* (fraud or abuse, decided by InvoHub: nobody can use it, not even the owner). Unpaid companies are never fully blocked.
- Company *closed* (`companies.closed_at`, built): the owner's choice, kept apart from the status, which only InvoHub sets. Read-only like an unpaid company; suspension still wins.
- User *suspended*: InvoHub staff only, applies everywhere (`UserService.suspendUser`).

**Database: Amazon Aurora PostgreSQL Serverless v2**, signing in with IAM. Real PostgreSQL, so production behaves like the `postgres:18` tests (if Serverless v2 doesn't offer 18 when the cluster is created, pin Docker and Testcontainers to the version it does). Capacity scales within a set range of ACUs and can pause when idle. Aurora DSQL was considered and dropped: no triggers, PL/pgSQL or extensions, retries on every write, a 3,000-row transaction limit, and no local equivalent.

**Audit log** (built: `AuditLog`, table `audit.events`). Events are written in the same transaction as the change they describe (`AuditLog.record`; `recordStandalone` for events that belong to no other change, like sign-ins). The app's database login may only insert and read there, and a trigger refuses changes from every login, migrations included. Recent months stay in Postgres for the activity page; the permanent copy goes to S3 with Object Lock (see the archive job below).

**Public IDs outside the app.** Internal (numeric) IDs are only used inside the app and in Postgres. Everything stored or shown elsewhere uses public IDs: URLs and the API, Redis keys, the session, logs, exports.

**Two database logins.** Migrations run as the schema owner (`spring.flyway.user`); the app connects with its own login (`spring.datasource.username`), which can read and write data but never change the schema. Grants are in `V1__initial_setup.sql`. In production both sign in with IAM (their database roles are granted `rds_iam`) through AWS's Advanced JDBC Wrapper; the master password, managed by RDS in Secrets Manager, is for emergencies only.

Events to record (the `user.*` ones are recorded already):

| | |
|---|---|
| Users | `user.created`, `user.signed_in`, `user.signed_out`, `user.sign_in_refused`, `user.suspended`, `user.unsuspended` |
| Companies | `company.created`, `company.updated` (before/after), `company.status_changed`, `company.closed`, `company.reopened`, `company.ownership_transferred` |
| Ownership offers | `ownership.offered`, `ownership.cancelled`, `ownership.declined` |
| Members | `member.added`, `member.role_changed`, `member.removed`, `member.left` |
| Invitations | `invitation.sent`, `invitation.revoked`, `invitation.accepted`, `invitation.declined`, and `member.added` on joining |
| Later | `invoice.issued`, `invoice.voided`, `credit_note.issued`, `export.downloaded` |

Reads and page views are not recorded.

**Earlier decisions (sign-in):**
- No new sign-in link while one is outstanding (one hour). This is deliberate: it stops inbox flooding.
- Per-IP rate limiting is handled by the CDN, not the app.
- Sessions end after 2 hours of inactivity and at most 7 days after sign-in.
- Suspending a user must go through `UserService.suspendUser`, which ends their sessions. Changing the database directly does not.

## Next up

Built so far: companies and roles, translations, permission checks, members, invitations, UI polish (the account menu, our own confirmation dialog, consistent selects), company settings, ownership transfer and closing. Every new page and email is written in both languages, and every company-scoped operation goes through the permission checks.

1. **Contacts:** customers and suppliers in one list; businesses or individuals, with the tax ID checked by the contact's country's rules.
2. **Items:** products and services, with price, unit and tax rate; stock tracking switched on per item.
3. **Sales invoices:** drafts, then issuing with numbering per company, immutable once issued, corrections through credit notes; a PDF with the logo. Lines pick from the items, or a new item typed on the line.
4. **Purchase invoices:** entered by hand (supplier, date, number, lines matched to items, at their cost). Owners and admins only.
5. **Stock:** the movement ledger above, fed by issued sales invoices, credit notes, purchase invoices and adjustments.
6. **myDATA connection and AADE registry lookup** (Greece only). Also sending issued invoices to myDATA (their MARK and QR code), and importing the invoices other businesses issued to the company as purchase invoices, if AADE's API allows it as expected.
   - myDATA: the company enters its own myDATA user ID and key, stored encrypted; InvoHub checks them against AADE, which completes the checklist's last step.
   - Registry: fill in a Greek company's legal name, trade name, address, tax office and activities from its ΑΦΜ through AADE's RgWsPublic2 service (SOAP, WS-Security username/password). Needs AADE access codes: InvoHub's own, which need an ΑΦΜ of its own, or optionally the company's. Fall back to manual entry when AADE doesn't answer.

## Later

- **Audit archive job** (separate PR; design below) and monthly partitioning of the audit table, which also gives the job its by-time lookups (the existing indexes are by company, actor and target).
- **User names** (first and last, optional): shown on the account button instead of the email, and on the Team page next to each email.
- **Activity page** for owners and admins; a "your sign-in history" page for users.
- **InvoHub staff tool:** suspend users, change company status.
- **Billing:** billing profiles, where one payer covers several companies with one bill (`companies.billing_profile_id`); unpaid leads to a grace period, then read-only.
- **Permanently deleting a closed company**, by its owner only: warns that they may have to keep their records, asks for the company's name, then removes its data from Postgres and S3 (the audit log keeps its history).
- **Archive storage** for closed companies' data, if the database ever needs it (a cost saving only; reopening would bring it back first).
- **Stock value and profit:** average cost, worked out from the recorded costs, then cost of sales and margins in reports. Several warehouses.
- **Invoices, beyond the first version:** the invoicing setup checklist screens and the block on issuing until it is complete; numbering per company, immutable once issued, corrections through credit notes. Rules depend on the country (required fields, numbering, tamper-evidence, mandatory e-invoicing). For **Greece**, check the current rules when designing this:
  - the issuer's tax office (ΔΟΥ) and main activity on invoices (both already part of the company's details)
  - reporting every invoice to AADE's myDATA
  - mandatory B2B e-invoicing, being phased in: each invoice is transmitted (to myDATA or through a certified provider) and gets back its MARK and a QR code, both printed on the invoice
- **Contacts:** "copy from another company".
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
- The Aurora PostgreSQL Serverless v2 cluster, with IAM authentication enabled: the app's database role and the migration (schema owner) role are granted `rds_iam`, the app's with no rights of its own (the migrations grant them; locally `backend/docker/postgres-init/create-app-login.sh` does the equivalent). The app and Flyway connect through AWS's Advanced JDBC Wrapper with its IAM plugin, tried once on staging since it can't be locally.
- Monitoring: Performance Insights, CloudWatch alarms (CPU, capacity, connections, latency) and a slow-query log. A load test against staging decides the capacity range.
- Environment: `SPRING_PROFILES_ACTIVE=prod`, `INVOHUB_AUTH_FRONTEND_URL` (required), `SPRING_DATASOURCE_URL` (an `aws-wrapper` URL with the IAM plugin) and `SPRING_DATASOURCE_USERNAME` for the app's role, `SPRING_FLYWAY_USER` for the schema owner (no passwords: IAM tokens), `SPRING_DATA_REDIS_*` with `SPRING_DATA_REDIS_SSL_ENABLED=true` (ElastiCache Serverless requires TLS), `SPRING_MAIL_*`, `INVOHUB_MAIL_FROM`.
- Run once against a real ElastiCache Serverless cache: its sharding can't be reproduced locally.
- Per-IP rate limiting at the CDN.
- Email sending (e.g. Amazon SES): handle bounces and complaints, stop sending to addresses that bounce or complain, and alert on a rising complaint rate. Invitation emails go to addresses other users type in, so they are the ones to watch.
- The S3 bucket with Object Lock, and the audit archive job.
- The S3 bucket for files: private (Block Public Access on), encrypted, with the app's IAM role allowed only to read, write, copy and delete objects in it. Also:
  - a lifecycle rule expiring `pending/` after a day (locally the app sets it up itself);
  - CORS allowing `PUT` from the frontend's origin, with the `Content-Type` header;
  - CloudFront in front of `logos/` only, reading through Origin Access Control, on its own domain (not the app's), with a response headers policy sending `X-Content-Type-Options: nosniff`;
  - if the frontend gets a Content-Security-Policy, allow the bucket for uploads (`connect-src`) and CloudFront for images (`img-src`);
  - environment: `INVOHUB_STORAGE_BUCKET`, `INVOHUB_STORAGE_PUBLIC_URL` (the CloudFront domain, e.g. `https://files.invohub.app`), and `INVOHUB_STORAGE_REGION` if not `eu-central-1`. No keys: the default AWS credentials are used.
- Terms of service, privacy policy and data processing agreement, checked by a lawyer: InvoHub keeps a closed company's data until its owner deletes it, and processes companies' invoices and customers on their behalf.
- From then on, schema changes go into new Flyway migrations instead of editing `V1__initial_setup.sql`.
