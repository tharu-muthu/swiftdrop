-- One shared schema. Additive migration: V1 and existing data are left intact.
-- UUIDs are also generated here so SQL clients do not depend on Hibernate.
CREATE TABLE swiftdrop.users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email varchar(320) NOT NULL,
    email_normalized varchar(320) GENERATED ALWAYS AS (lower(btrim(email))) STORED,
    full_name varchar(200) NOT NULL,
    phone varchar(32),
    password_hash varchar(255),
    active boolean NOT NULL DEFAULT true,
    authorization_version bigint NOT NULL DEFAULT 0 CHECK (authorization_version >= 0),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email_normalized UNIQUE (email_normalized),
    CONSTRAINT ck_users_email_nonblank CHECK (length(btrim(email)) > 0),
    CONSTRAINT ck_users_name_nonblank CHECK (length(btrim(full_name)) > 0),
    CONSTRAINT ck_users_password_hash_nonblank CHECK (password_hash IS NULL OR length(btrim(password_hash)) > 0),
    CONSTRAINT ck_users_updated_at CHECK (updated_at >= created_at)
);

CREATE TABLE swiftdrop.roles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(32) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_roles_name UNIQUE (name),
    CONSTRAINT ck_roles_name CHECK (name IN ('CUSTOMER', 'MERCHANT', 'COURIER', 'DISPATCHER_ADMIN'))
);

-- Stable reference-data IDs; no user, password or administrator account is seeded.
INSERT INTO swiftdrop.roles (id, name) VALUES
    ('00000000-0000-4000-8000-000000000001', 'CUSTOMER'),
    ('00000000-0000-4000-8000-000000000002', 'MERCHANT'),
    ('00000000-0000-4000-8000-000000000003', 'COURIER'),
    ('00000000-0000-4000-8000-000000000004', 'DISPATCHER_ADMIN');

CREATE TABLE swiftdrop.user_roles (
    user_id uuid NOT NULL REFERENCES swiftdrop.users (id),
    role_id uuid NOT NULL REFERENCES swiftdrop.roles (id),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX ix_user_roles_role_user ON swiftdrop.user_roles (role_id, user_id);

CREATE TABLE swiftdrop.oauth_accounts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES swiftdrop.users (id),
    provider varchar(32) NOT NULL,
    provider_subject varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_oauth_accounts_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uq_oauth_accounts_user_provider UNIQUE (user_id, provider),
    CONSTRAINT ck_oauth_accounts_provider CHECK (provider ~ '^[a-z][a-z0-9_-]*$'),
    CONSTRAINT ck_oauth_accounts_subject CHECK (length(btrim(provider_subject)) > 0)
);

-- id is the stable future JWT sid. A session owns exactly one refresh-token family.
CREATE TABLE swiftdrop.auth_sessions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES swiftdrop.users (id),
    family_id uuid NOT NULL DEFAULT gen_random_uuid(),
    authorization_version bigint NOT NULL CHECK (authorization_version >= 0),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    revocation_reason varchar(100),
    CONSTRAINT uq_auth_sessions_family UNIQUE (family_id),
    CONSTRAINT uq_auth_sessions_id_expiry UNIQUE (id, expires_at),
    CONSTRAINT ck_auth_sessions_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_auth_sessions_updated_at CHECK (updated_at >= created_at),
    CONSTRAINT ck_auth_sessions_revocation CHECK (
        (revoked_at IS NULL AND revocation_reason IS NULL) OR
        (revoked_at IS NOT NULL AND revoked_at >= created_at AND
         revocation_reason IS NOT NULL AND length(btrim(revocation_reason)) > 0)
    )
);
CREATE INDEX ix_auth_sessions_user ON swiftdrop.auth_sessions (user_id);
CREATE INDEX ix_auth_sessions_active_user ON swiftdrop.auth_sessions (user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_auth_sessions_expiry ON swiftdrop.auth_sessions (expires_at);

CREATE TABLE swiftdrop.refresh_tokens (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id uuid NOT NULL,
    token_hash varchar(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    revoked_at timestamptz,
    replaced_by uuid,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT uq_refresh_tokens_session_id UNIQUE (session_id, id),
    CONSTRAINT uq_refresh_tokens_replacement UNIQUE (replaced_by),
    -- Every rotation keeps the family's original absolute expiry.
    CONSTRAINT fk_refresh_tokens_session_expiry FOREIGN KEY (session_id, expires_at)
        REFERENCES swiftdrop.auth_sessions (id, expires_at),
    -- Deferred so a transaction can consume A, insert B, then link A -> B.
    -- The composite FK prevents linking a token from another session/family.
    CONSTRAINT fk_refresh_tokens_replacement FOREIGN KEY (session_id, replaced_by)
        REFERENCES swiftdrop.refresh_tokens (session_id, id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_refresh_tokens_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_refresh_tokens_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_refresh_tokens_consumed CHECK (consumed_at IS NULL OR consumed_at >= created_at),
    CONSTRAINT ck_refresh_tokens_revoked CHECK (revoked_at IS NULL OR revoked_at >= created_at),
    CONSTRAINT ck_refresh_tokens_replacement CHECK (
        replaced_by IS NULL OR (replaced_by <> id AND consumed_at IS NOT NULL)
    )
);
CREATE UNIQUE INDEX uq_refresh_tokens_current_session ON swiftdrop.refresh_tokens (session_id)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;
CREATE INDEX ix_refresh_tokens_expiry ON swiftdrop.refresh_tokens (expires_at);

CREATE TABLE swiftdrop.password_reset_tokens (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES swiftdrop.users (id),
    token_hash varchar(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    revoked_at timestamptz,
    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_password_reset_tokens_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_password_reset_tokens_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_password_reset_tokens_used CHECK (used_at IS NULL OR used_at >= created_at),
    CONSTRAINT ck_password_reset_tokens_revoked CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);
CREATE UNIQUE INDEX uq_password_reset_tokens_current_user ON swiftdrop.password_reset_tokens (user_id)
    WHERE used_at IS NULL AND revoked_at IS NULL;
CREATE INDEX ix_password_reset_tokens_user ON swiftdrop.password_reset_tokens (user_id);
CREATE INDEX ix_password_reset_tokens_expiry ON swiftdrop.password_reset_tokens (expires_at);

-- No existing audit mechanism was present. Keep this scoped to identity administration.
CREATE TABLE swiftdrop.identity_audit_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_kind varchar(16) NOT NULL,
    actor_user_id uuid REFERENCES swiftdrop.users (id),
    target_user_id uuid NOT NULL REFERENCES swiftdrop.users (id),
    action varchar(64) NOT NULL,
    reason varchar(500) NOT NULL,
    before_state jsonb NOT NULL DEFAULT '{}'::jsonb,
    after_state jsonb NOT NULL DEFAULT '{}'::jsonb,
    correlation_id varchar(100),
    occurred_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_identity_audit_actor CHECK (
        (actor_kind = 'USER' AND actor_user_id IS NOT NULL) OR
        (actor_kind = 'SYSTEM' AND actor_user_id IS NULL)
    ),
    CONSTRAINT ck_identity_audit_action CHECK (length(btrim(action)) > 0),
    CONSTRAINT ck_identity_audit_reason CHECK (length(btrim(reason)) > 0),
    CONSTRAINT ck_identity_audit_states CHECK (
        jsonb_typeof(before_state) = 'object' AND jsonb_typeof(after_state) = 'object'
    )
);
CREATE INDEX ix_identity_audit_target_time ON swiftdrop.identity_audit_events (target_user_id, occurred_at DESC);
CREATE INDEX ix_identity_audit_actor_time ON swiftdrop.identity_audit_events (actor_user_id, occurred_at DESC);

CREATE FUNCTION swiftdrop.identity_touch_updated_at() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := clock_timestamp();
    RETURN NEW;
END;
$$;
CREATE TRIGGER users_updated_at BEFORE UPDATE ON swiftdrop.users
    FOR EACH ROW EXECUTE FUNCTION swiftdrop.identity_touch_updated_at();
CREATE TRIGGER auth_sessions_updated_at BEFORE UPDATE ON swiftdrop.auth_sessions
    FOR EACH ROW EXECUTE FUNCTION swiftdrop.identity_touch_updated_at();

CREATE FUNCTION swiftdrop.identity_audit_reject_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Identity audit events are append-only' USING ERRCODE = '23514';
END;
$$;
CREATE TRIGGER identity_audit_append_only BEFORE UPDATE OR DELETE ON swiftdrop.identity_audit_events
    FOR EACH ROW EXECUTE FUNCTION swiftdrop.identity_audit_reject_mutation();

COMMENT ON COLUMN swiftdrop.users.email_normalized IS 'Database-generated lower(btrim(email)); no provider-specific alias rewriting';
COMMENT ON COLUMN swiftdrop.users.password_hash IS 'Adaptive password hash; NULL for an account with no local password';
COMMENT ON COLUMN swiftdrop.refresh_tokens.token_hash IS 'Lowercase hexadecimal SHA-256 of a high-entropy opaque token; never the raw token';
COMMENT ON COLUMN swiftdrop.password_reset_tokens.token_hash IS 'Lowercase hexadecimal SHA-256 of a high-entropy opaque token; never the raw token';
COMMENT ON TABLE swiftdrop.auth_sessions IS 'Stable sign-in session and revocation boundary for one refresh family';
COMMENT ON TABLE swiftdrop.identity_audit_events IS 'Append-only identity audit; callers must allowlist safe state fields and exclude credentials';
