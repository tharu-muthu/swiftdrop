-- Preserve users and existing sessions. Password accounts have no prior proof of inbox ownership.
ALTER TABLE swiftdrop.users ADD COLUMN email_verified_at timestamptz;

-- Google OIDC links were created only after a verified email claim was checked.
UPDATE swiftdrop.users u SET email_verified_at = CURRENT_TIMESTAMP
WHERE EXISTS (SELECT 1 FROM swiftdrop.oauth_accounts oa
              WHERE oa.user_id = u.id AND oa.provider = 'google');

CREATE TABLE swiftdrop.email_verification_tokens (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES swiftdrop.users (id),
    token_hash varchar(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    revoked_at timestamptz,
    CONSTRAINT uq_email_verification_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_email_verification_tokens_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_email_verification_tokens_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_email_verification_tokens_used CHECK (used_at IS NULL OR used_at >= created_at),
    CONSTRAINT ck_email_verification_tokens_revoked CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);
CREATE UNIQUE INDEX uq_email_verification_tokens_current_user
    ON swiftdrop.email_verification_tokens (user_id)
    WHERE used_at IS NULL AND revoked_at IS NULL;
CREATE INDEX ix_email_verification_tokens_user ON swiftdrop.email_verification_tokens (user_id);
CREATE INDEX ix_email_verification_tokens_expiry ON swiftdrop.email_verification_tokens (expires_at);

COMMENT ON COLUMN swiftdrop.users.email_verified_at IS 'Null until inbox ownership or a verified OIDC email is established';
COMMENT ON TABLE swiftdrop.email_verification_tokens IS 'Single-use, hashed inbox verification links';
