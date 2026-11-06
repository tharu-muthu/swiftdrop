-- M9 persistence. merchant_id is resolved and authorized through the M2 service
-- contract until the merchant profile table is merged into the shared schema.
CREATE TABLE swiftdrop.webhook_subscriptions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id uuid NOT NULL,
    callback_url varchar(2048) NOT NULL,
    event_types jsonb NOT NULL DEFAULT '[]'::jsonb,
    encrypted_signing_secret bytea NOT NULL,
    signing_secret_nonce bytea NOT NULL,
    secret_key_version varchar(32) NOT NULL DEFAULT 'v1',
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_webhook_subscription_merchant_url UNIQUE (merchant_id, callback_url),
    CONSTRAINT ck_webhook_subscription_url CHECK (length(btrim(callback_url)) > 0),
    CONSTRAINT ck_webhook_subscription_events CHECK (jsonb_typeof(event_types) = 'array'),
    CONSTRAINT ck_webhook_subscription_secret CHECK (octet_length(encrypted_signing_secret) > 0),
    CONSTRAINT ck_webhook_subscription_nonce CHECK (octet_length(signing_secret_nonce) = 12),
    CONSTRAINT ck_webhook_subscription_updated CHECK (updated_at >= created_at)
);
CREATE INDEX ix_webhook_subscriptions_merchant_active
    ON swiftdrop.webhook_subscriptions (merchant_id, active);

CREATE TABLE swiftdrop.webhook_deliveries (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id uuid NOT NULL REFERENCES swiftdrop.webhook_subscriptions (id),
    event_id uuid NOT NULL,
    event_type varchar(100) NOT NULL,
    payload_body text NOT NULL,
    delivery_state varchar(24) NOT NULL DEFAULT 'PENDING',
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at timestamptz,
    last_http_status integer,
    last_error varchar(1000),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at timestamptz,
    CONSTRAINT uq_webhook_delivery_event UNIQUE (subscription_id, event_id),
    CONSTRAINT ck_webhook_delivery_state CHECK (delivery_state IN ('PENDING', 'RETRYING', 'DELIVERED', 'FAILED')),
    CONSTRAINT ck_webhook_delivery_status CHECK (last_http_status IS NULL OR last_http_status BETWEEN 100 AND 599),
    CONSTRAINT ck_webhook_delivery_completed CHECK (completed_at IS NULL OR completed_at >= created_at)
);
CREATE INDEX ix_webhook_deliveries_due ON swiftdrop.webhook_deliveries (delivery_state, next_attempt_at);
CREATE INDEX ix_webhook_deliveries_event ON swiftdrop.webhook_deliveries (event_id);

CREATE TABLE swiftdrop.webhook_delivery_attempts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_id uuid NOT NULL REFERENCES swiftdrop.webhook_deliveries (id),
    attempt_number integer NOT NULL CHECK (attempt_number > 0),
    started_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at timestamptz,
    http_status integer,
    outcome varchar(24) NOT NULL,
    error_message varchar(1000),
    CONSTRAINT uq_webhook_attempt_number UNIQUE (delivery_id, attempt_number),
    CONSTRAINT ck_webhook_attempt_status CHECK (http_status IS NULL OR http_status BETWEEN 100 AND 599),
    CONSTRAINT ck_webhook_attempt_completed CHECK (completed_at IS NULL OR completed_at >= started_at)
);
CREATE INDEX ix_webhook_attempts_delivery_time
    ON swiftdrop.webhook_delivery_attempts (delivery_id, started_at DESC);

CREATE FUNCTION swiftdrop.webhook_touch_updated_at() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := clock_timestamp();
    RETURN NEW;
END;
$$;
CREATE TRIGGER webhook_subscriptions_updated_at BEFORE UPDATE ON swiftdrop.webhook_subscriptions
    FOR EACH ROW EXECUTE FUNCTION swiftdrop.webhook_touch_updated_at();

COMMENT ON TABLE swiftdrop.webhook_subscriptions IS
    'M9 merchant callback subscriptions; merchant approval and ownership come from M2.';
COMMENT ON COLUMN swiftdrop.webhook_subscriptions.encrypted_signing_secret IS
    'AES-GCM ciphertext. Plaintext signing secrets are never stored or returned.';
COMMENT ON TABLE swiftdrop.webhook_deliveries IS
    'Durable idempotent delivery records for committed merchant events.';
