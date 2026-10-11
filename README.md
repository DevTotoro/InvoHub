# InvoHub

Invoicing app. Spring Boot (Kotlin) API with a SvelteKit frontend; sign-in is passwordless, by magic link.

| | |
|---|---|
| `backend/` | Spring Boot 4, Kotlin, Java 25. REST for auth, GraphQL for data. Postgres (Flyway) and Valkey/Redis (sessions, magic links). |
| `frontend/` | SvelteKit 3, Svelte 5, Tailwind 4. Client-side rendered; talks to the API under `/api`. |

## Prerequisites

- **JDK 25.** The Gradle toolchain is pinned to Eclipse Temurin 25 (the distribution CI uses) and picks it up from the usual install locations.
- **Node.js 24** and **pnpm 12** (`npm install -g pnpm@12`).
- **Docker**, for the local Postgres/Redis/Mailpit stack and for the backend tests (Testcontainers).

<details>
<summary>Using Rancher Desktop?</summary>

Testcontainers looks for Docker at `/var/run/docker.sock`. Either enable administrative access in Rancher Desktop (Preferences → Application), which creates that socket, or point Testcontainers at Rancher's:

```properties
# ~/.testcontainers.properties
docker.host=unix:///Users/<you>/.rd/docker.sock
```

```sh
# ~/.zshrc
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```
</details>

## Running locally

```sh
# Backend: Postgres, Redis and Mailpit, then the API on http://localhost:8080
cd backend
cp .env.example .env        # once; compose.yaml reads its settings from here
docker compose up -d
./gradlew bootRun           # runs with the `local` profile

# Frontend, in a second terminal: http://localhost:5173
cd frontend
pnpm install
pnpm dev                    # proxies /api to the backend
```

To sign in, open http://localhost:5173, enter any email address and open the link from **Mailpit** at http://localhost:8025 (it catches all outgoing mail). The first sign-in for an address creates the account.

Running the backend from IntelliJ instead of `bootRun`: set `SPRING_PROFILES_ACTIVE=local` and the working directory to `backend/`, so `.env` is found.

## Tests and checks

```sh
cd backend
./gradlew check             # ktlint + all tests; needs Docker

cd frontend
pnpm check                  # svelte-check / TypeScript
pnpm lint                   # Prettier + ESLint
pnpm test:unit -- --run     # Vitest: unit tests and component tests in a browser
pnpm exec playwright test   # end-to-end, against a mocked API
```

Formatters: `./gradlew ktlintFormat` and `pnpm format`.

CI (`.github/workflows/ci.yaml`) runs the same checks on pull requests to `main`, for whichever of `backend/` and `frontend/` changed.

## How sign-in works

1. `/login` posts the email to `POST /api/auth/request`. A single-use token is generated, its hash stored in Redis for an hour, and a link to `/auth/verify?token=…` emailed. While a link is outstanding, no new one is sent to that address.
2. `/auth/verify` waits for the user to press **Continue**, then posts the token to `POST /api/auth/verify`. The button keeps email scanners, which open links before the user does, from using up the token.
3. The backend finds or creates the user and starts a session, stored in Redis behind an HttpOnly `SESSION` cookie. A session ends after **2 hours without requests** and at most **7 days** after sign-in.
4. Requests that change something carry a CSRF token: the frontend copies the `XSRF-TOKEN` cookie into an `X-XSRF-TOKEN` header (handled by `frontend/src/lib/api/client.ts`).

Locking an account with `UserService.lockUser` ends all of the user's sessions, and verifying a link then fails with `account_locked`. Lock through that method: setting `users.locked` directly in the database blocks new sign-ins, but leaves current sessions running until they expire.

## Configuration

Spring profiles: `local` (set by `bootRun`; connection defaults from `.env`, GraphiQL on) and `prod` (structured logging). The base `application.yaml` is safe for production and has no connection defaults, so a deployment with missing settings fails at startup.

Production environment:

| Variable | |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `INVOHUB_AUTH_FRONTEND_URL` | Public URL of the frontend; magic links point here. **Required.** |
| `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` | Postgres |
| `SPRING_DATA_REDIS_HOST`, `_PORT`, `_USERNAME`, `_PASSWORD` | Valkey/Redis |
| `SPRING_DATA_REDIS_SSL_ENABLED` | `true` for ElastiCache Serverless, which requires TLS |
| `SPRING_MAIL_HOST`, `_PORT`, `_USERNAME`, `_PASSWORD` | SMTP |
| `INVOHUB_MAIL_FROM` | Sender address, default `no-reply@invohub.com` |

Optional: `INVOHUB_AUTH_MAGIC_LINK_TTL` (default `1h`), `INVOHUB_AUTH_SESSION_MAX_AGE` (`7d`), `SERVER_SERVLET_SESSION_TIMEOUT` (inactivity, `2h`).

## Deploying

- **Serve the frontend and `/api` from the same site** (for example by routing `/api` to the backend at the CDN or on Vercel). The session cookie is only sent to the site that set it.
- **HTTPS only**: the session cookie is marked `Secure`.
- **ElastiCache Serverless**: supported. The app never uses `CONFIG`, `PSUBSCRIBE` or `KEYS`, which Serverless blocks, and the backend tests run against a Redis with those commands disabled to keep it that way. Try a real Serverless cache once before launch, since its sharding can't be reproduced locally.

## Database migrations

Until the first deployment, the whole schema lives in `backend/src/main/resources/db/migration/V1__initial_setup.sql` and is edited in place; after editing it, reset your local database with `docker compose down -v && docker compose up -d` (this deletes local data). After the first deployment, every change goes into a new `V<n>__….sql` migration.
