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

-- Audit log: what happened, who did it, when. Append-only: the app's login may only add and read events, and
-- a trigger refuses changes from every login, migrations included. No foreign keys: events must outlive the
-- users and companies they mention. The database sets the time.
CREATE SCHEMA audit;
GRANT USAGE ON SCHEMA audit TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA audit GRANT SELECT, INSERT ON TABLES TO ${app_user};

CREATE TABLE audit.events (
    id BIGINT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT clock_timestamp(),
    action VARCHAR(100) NOT NULL,
    actor_user_id BIGINT,
    company_id BIGINT,
    target_type VARCHAR(50),
    target_id BIGINT,
    details JSONB NOT NULL DEFAULT '{}',
    request_id VARCHAR(255),
    --
    CONSTRAINT pk_audit_events PRIMARY KEY (id)
);

CREATE INDEX ix_audit_events_company ON audit.events (company_id, occurred_at) WHERE company_id IS NOT NULL;
CREATE INDEX ix_audit_events_actor ON audit.events (actor_user_id, occurred_at) WHERE actor_user_id IS NOT NULL;
CREATE INDEX ix_audit_events_target ON audit.events (target_type, target_id, occurred_at);

CREATE FUNCTION audit.refuse_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit.events is append-only';
END;
$$;

CREATE TRIGGER events_append_only BEFORE UPDATE OR DELETE ON audit.events
    FOR EACH ROW EXECUTE FUNCTION audit.refuse_change();
CREATE TRIGGER events_no_truncate BEFORE TRUNCATE ON audit.events
    FOR EACH STATEMENT EXECUTE FUNCTION audit.refuse_change();
