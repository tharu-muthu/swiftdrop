-- M2: merchant accounts and API credentials

CREATE TABLE merchants (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id    UUID NOT NULL REFERENCES users (id),
    business_name    VARCHAR(150) NOT NULL,
    contact_email    VARCHAR(254) NOT NULL,
    contact_phone    VARCHAR(30),
    business_address VARCHAR(300),
    approval_status  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    review_reason    VARCHAR(500),
    reviewed_by      UUID REFERENCES users (id),
    reviewed_at      TIMESTAMPTZ,
    version          BIGINT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_merchants_owner UNIQUE (owner_user_id),
    CONSTRAINT ck_merchants_status
        CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    CONSTRAINT ck_merchants_reject_reason
        CHECK (approval_status <> 'REJECTED' OR review_reason IS NOT NULL)
);

CREATE TABLE api_credentials (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id  UUID NOT NULL REFERENCES merchants (id),
    key_prefix   VARCHAR(16) NOT NULL,
    secret_hash  VARCHAR(64) NOT NULL,
    scopes       VARCHAR(200) NOT NULL DEFAULT 'quote,order',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ,
    CONSTRAINT uq_api_credentials_prefix UNIQUE (key_prefix),
    CONSTRAINT ck_api_credentials_hash CHECK (secret_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_api_credentials_merchant ON api_credentials (merchant_id);