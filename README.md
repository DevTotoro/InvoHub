# InvoHub

An invoicing app. The backend is a Spring Boot (Kotlin) API in `backend/`; the frontend is a SvelteKit app in `frontend/`. Sign-in is passwordless: you get a sign-in link by email.

## Requirements

- JDK 25 (Eclipse Temurin)
- Node.js 24 and pnpm 12 (`npm install -g pnpm@12`)
- Docker

## Running it

Start the backend (Postgres, Redis, a local mail server and Silo for files, then the API on http://localhost:8080):

```sh
cd backend
cp .env.example .env
docker compose up -d
./gradlew bootRun
```

In a second terminal, start the frontend on http://localhost:5173:

```sh
cd frontend
pnpm install
pnpm dev
```

Open http://localhost:5173 and sign in with any email address. The sign-in email doesn't really get sent: open it in Mailpit at http://localhost:8025. Uploaded files, such as logos, are in Silo's console at http://localhost:9101 (sign in with `STORAGE_ACCESS_KEY` and `STORAGE_SECRET_KEY` from `.env`).

## Translations

InvoHub is in English and Greek. The app's texts are in `frontend/messages/` (`en.json`, `el.json`) and the emails' in `backend/src/main/resources/i18n/`. Add each new text to every language: the tests fail when one is missing.
