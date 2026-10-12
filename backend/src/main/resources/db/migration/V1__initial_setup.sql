-- Privileges. This migration runs as the owner of the schema; the app connects as ${app_user}, which can
-- read and write data but never change the schema. Tables created from here on get these grants by default.
GRANT USAGE ON SCHEMA public TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${app_user};

CREATE TABLE users (
    id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    --
    email VARCHAR(255) NOT NULL,
    -- For the app and the user's emails. A VARCHAR with a CHECK, like company_members.role
    language VARCHAR(2) NOT NULL DEFAULT 'EN',
    suspended BOOLEAN NOT NULL DEFAULT FALSE,
    suspension_reason VARCHAR(255),
    --
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    --
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    -- Emails are stored normalized, which makes uq_users_email reject case variants of the same address
    CONSTRAINT ck_users_email_normalized CHECK (email = lower(btrim(email))),
    CONSTRAINT ck_users_language CHECK (language IN ('EN', 'EL'))
);

-- Companies. Created with just a name and a country; the invoicing details stay empty until the company works
-- through its invoicing setup checklist. The tax ID is deliberately not unique.
CREATE TABLE companies (
    id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    --
    name VARCHAR(200) NOT NULL,
    country VARCHAR(2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    -- ACTIVE, READ_ONLY (unpaid) or SUSPENDED (by InvoHub staff). A VARCHAR with a CHECK, like company_members.role
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    owner_user_id BIGINT NOT NULL,
    --
    tax_id VARCHAR(50),
    tax_id_verified_at TIMESTAMP WITH TIME ZONE,
    legal_name VARCHAR(255),
    address_street VARCHAR(255),
    address_city VARCHAR(100),
    address_postal_code VARCHAR(20),
    country_details JSONB NOT NULL DEFAULT '{}',
    --
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    --
    CONSTRAINT pk_companies PRIMARY KEY (id),
    CONSTRAINT fk_companies_owner FOREIGN KEY (owner_user_id) REFERENCES users (id),
    CONSTRAINT ck_companies_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_companies_country CHECK (country ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_companies_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_companies_status CHECK (status IN ('ACTIVE', 'READ_ONLY', 'SUSPENDED'))
);

CREATE INDEX ix_companies_owner ON companies (owner_user_id);

-- Everyone with access to a company except its owner, who is companies.owner_user_id. A membership is just a
-- user in a company, so that pair is the key. The role is a VARCHAR with a CHECK rather than an enum type, so
-- roles can be added or removed with a simple migration.
CREATE TABLE company_members (
    company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    --
    role VARCHAR(20) NOT NULL,
    --
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    --
    CONSTRAINT pk_company_members PRIMARY KEY (company_id, user_id),
    CONSTRAINT fk_company_members_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_company_members_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_company_members_role CHECK (role IN ('ADMIN', 'MEMBER', 'VIEWER'))
);

CREATE INDEX ix_company_members_user ON company_members (user_id);

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
