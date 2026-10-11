#!/bin/sh
# Runs once, when the database volume is first created. Creates the login the app connects with; it gets its
# privileges from the Flyway migrations, which run as the database owner (POSTGRES_USER).
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<SQL
CREATE ROLE "$DB_APP_USER" LOGIN PASSWORD '$DB_APP_PASSWORD';
SQL
