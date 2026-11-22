-- Coordinate V13 with the team before applying on a shared development branch.
CREATE TABLE swiftdrop.account_action_tokens (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id uuid NOT NULL REFERENCES swiftdrop.users(id),
 session_id uuid NOT NULL REFERENCES swiftdrop.auth_sessions(id),
 kind varchar(24) NOT NULL CHECK (kind IN ('EMAIL_CHANGE','GOOGLE_REAUTH')),
 token_hash varchar(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
 authorization_version bigint NOT NULL CHECK (authorization_version >= 0),
 pending_email varchar(320),
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 expires_at timestamptz NOT NULL CHECK (expires_at > created_at),
 used_at timestamptz,
 revoked_at timestamptz,
 CHECK ((kind = 'EMAIL_CHANGE' AND pending_email IS NOT NULL) OR (kind = 'GOOGLE_REAUTH' AND pending_email IS NULL)),
 CHECK (used_at IS NULL OR used_at >= created_at),
 CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);
CREATE INDEX ix_account_action_user ON swiftdrop.account_action_tokens(user_id);
CREATE UNIQUE INDEX uq_account_action_current ON swiftdrop.account_action_tokens(user_id,kind) WHERE used_at IS NULL AND revoked_at IS NULL;
CREATE INDEX ix_account_action_expiry ON swiftdrop.account_action_tokens(expires_at);
CREATE TABLE swiftdrop.saved_addresses (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id uuid NOT NULL REFERENCES swiftdrop.users(id),
 label varchar(60) NOT NULL CHECK(length(btrim(label)) > 0),
 contact_name varchar(200) NOT NULL CHECK(length(btrim(contact_name)) > 0),
 contact_phone varchar(32) NOT NULL,
 address_line1 varchar(140) NOT NULL CHECK(length(btrim(address_line1)) > 0),
 address_line2 varchar(140) NOT NULL DEFAULT '',
 city varchar(100) NOT NULL CHECK(length(btrim(city)) > 0),
 postal_code varchar(5) NOT NULL CHECK(postal_code ~ '^[0-9]{5}$'),
 default_pickup boolean NOT NULL DEFAULT false,
 version bigint NOT NULL DEFAULT 0 CHECK(version >= 0),
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_saved_addresses_owner ON swiftdrop.saved_addresses(user_id);
CREATE UNIQUE INDEX uq_saved_addresses_default ON swiftdrop.saved_addresses(user_id) WHERE default_pickup;
