CREATE TABLE users (
    id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    --
    email VARCHAR(255) NOT NULL,
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    lock_reason VARCHAR(255),
    --
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    --
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    -- Emails are stored normalized, which makes uq_users_email reject case variants of the same address
    CONSTRAINT ck_users_email_normalized CHECK (email = lower(btrim(email)))
);
