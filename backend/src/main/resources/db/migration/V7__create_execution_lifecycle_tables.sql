-- M6 owns the canonical parcel lifecycle. M5 supplies the assigned courier
-- through the current assignment contract; no courier profile is duplicated here.
CREATE TABLE swiftdrop.parcel_lifecycles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id uuid NOT NULL UNIQUE REFERENCES swiftdrop.parcel_bookings (id),
    assigned_courier_id uuid NOT NULL REFERENCES swiftdrop.users (id),
    status varchar(24) NOT NULL DEFAULT 'CREATED',
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_parcel_lifecycles_status CHECK (
        status IN ('CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED')
    ),
    CONSTRAINT ck_parcel_lifecycles_version CHECK (version >= 0),
    CONSTRAINT ck_parcel_lifecycles_updated_at CHECK (updated_at >= created_at)
);

CREATE INDEX ix_parcel_lifecycles_courier_status
    ON swiftdrop.parcel_lifecycles (assigned_courier_id, status, updated_at DESC);

CREATE FUNCTION swiftdrop.lifecycle_touch_updated_at() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := clock_timestamp();
    RETURN NEW;
END;
$$;
CREATE TRIGGER parcel_lifecycles_updated_at BEFORE UPDATE ON swiftdrop.parcel_lifecycles
    FOR EACH ROW EXECUTE FUNCTION swiftdrop.lifecycle_touch_updated_at();

CREATE TABLE swiftdrop.parcel_status_history (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    lifecycle_id uuid NOT NULL REFERENCES swiftdrop.parcel_lifecycles (id),
    sequence_no bigint NOT NULL,
    previous_status varchar(24),
    new_status varchar(24) NOT NULL,
    actor_user_id uuid NOT NULL REFERENCES swiftdrop.users (id),
    failure_reason varchar(500),
    occurred_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_parcel_status_history_sequence UNIQUE (lifecycle_id, sequence_no),
    CONSTRAINT ck_parcel_status_history_previous CHECK (
        previous_status IS NULL OR previous_status IN
        ('CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED')
    ),
    CONSTRAINT ck_parcel_status_history_new CHECK (
        new_status IN ('CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED')
    ),
    CONSTRAINT ck_parcel_status_history_reason CHECK (
        new_status <> 'FAILED' OR (failure_reason IS NOT NULL AND length(btrim(failure_reason)) > 0)
    )
);

CREATE INDEX ix_parcel_status_history_lifecycle_time
    ON swiftdrop.parcel_status_history (lifecycle_id, occurred_at, sequence_no);

CREATE TABLE swiftdrop.delivery_proofs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    history_id uuid NOT NULL UNIQUE REFERENCES swiftdrop.parcel_status_history (id),
    recipient_name varchar(200) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_delivery_proofs_recipient_name CHECK (length(btrim(recipient_name)) > 0)
);

CREATE TABLE swiftdrop.outbox_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type varchar(100) NOT NULL,
    aggregate_id uuid NOT NULL,
    payload jsonb NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at timestamptz,
    attempts integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_outbox_events_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_outbox_events_published CHECK (published_at IS NULL OR published_at >= occurred_at)
);

CREATE INDEX ix_outbox_events_unpublished
    ON swiftdrop.outbox_events (occurred_at, id) WHERE published_at IS NULL;
