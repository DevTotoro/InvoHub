-- Privileges. This migration runs as the owner of the schema; the app connects as ${app_user}, which can
-- read and write data but never change the schema. Tables created from here on get these grants by default.
GRANT USAGE ON SCHEMA public TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${app_user};

CREATE TABLE users (
    id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    --
    email VARCHAR(255) NOT NULL,
    suspended BOOLEAN NOT NULL DEFAULT FALSE,
    suspension_reason VARCHAR(255),
    --
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    --
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    -- Emails are stored normalized, which makes uq_users_email reject case variants of the same address
    CONSTRAINT ck_users_email_normalized CHECK (email = lower(btrim(email)))
);
