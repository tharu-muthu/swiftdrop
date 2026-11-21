-- Additive integration of M2/M4 booking ownership and durable asynchronous work.
ALTER TABLE swiftdrop.parcel_bookings ALTER COLUMN customer_id DROP NOT NULL;
ALTER TABLE swiftdrop.parcel_bookings
    ADD COLUMN merchant_id uuid REFERENCES swiftdrop.merchants(id),
    ADD COLUMN quote_id uuid REFERENCES swiftdrop.quotations(id),
    ADD COLUMN idempotency_key varchar(100),
    ADD COLUMN request_hash varchar(64),
    ADD COLUMN sender_name varchar(120),
    ADD COLUMN sender_phone varchar(32),
    ADD COLUMN pickup_postal_code varchar(10),
    ADD COLUMN delivery_postal_code varchar(10),
    ADD COLUMN parcel_size varchar(24),
    ADD CONSTRAINT ck_booking_owner CHECK ((customer_id IS NULL) <> (merchant_id IS NULL)),
    ADD CONSTRAINT ck_booking_request CHECK ((idempotency_key IS NULL) = (request_hash IS NULL));
CREATE UNIQUE INDEX uq_booking_customer_request ON swiftdrop.parcel_bookings(customer_id,idempotency_key);
CREATE UNIQUE INDEX uq_booking_merchant_request ON swiftdrop.parcel_bookings(merchant_id,idempotency_key);
CREATE UNIQUE INDEX uq_booking_quote ON swiftdrop.parcel_bookings(quote_id) WHERE quote_id IS NOT NULL;
CREATE INDEX ix_booking_merchant_created ON swiftdrop.parcel_bookings(merchant_id,created_at DESC);

CREATE TABLE swiftdrop.integration_receipts (
    consumer varchar(40) NOT NULL,
    event_id uuid NOT NULL,
    received_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(consumer,event_id)
);
CREATE TABLE swiftdrop.notification_templates (
    status varchar(24) PRIMARY KEY,
    body varchar(500) NOT NULL CHECK(length(btrim(body)) > 0),
    enabled boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO swiftdrop.notification_templates(status,body) VALUES
 ('CREATED','SwiftDrop: {trackingNumber} has been booked.'),
 ('PICKED_UP','SwiftDrop: {trackingNumber} has been picked up.'),
 ('IN_TRANSIT','SwiftDrop: {trackingNumber} is in transit.'),
 ('OUT_FOR_DELIVERY','SwiftDrop: {trackingNumber} is out for delivery.'),
 ('DELIVERED','SwiftDrop: {trackingNumber} has been delivered.'),
 ('FAILED','SwiftDrop: delivery of {trackingNumber} failed. Contact the sender for assistance.');
CREATE TABLE swiftdrop.notification_jobs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id uuid NOT NULL UNIQUE,
    booking_id uuid NOT NULL REFERENCES swiftdrop.parcel_bookings(id),
    recipient_phone varchar(32) NOT NULL,
    message varchar(1000) NOT NULL,
    state varchar(24) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','RETRYING','SENT','DEAD')),
    attempts integer NOT NULL DEFAULT 0 CHECK(attempts >= 0),
    next_attempt_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error varchar(200),
    provider_reference varchar(100),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at timestamptz
);
CREATE TABLE swiftdrop.notification_attempts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id uuid NOT NULL REFERENCES swiftdrop.notification_jobs(id),
    attempt_no integer NOT NULL,
    successful boolean NOT NULL,
    failure_code varchar(200),
    attempted_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(job_id,attempt_no)
);
CREATE INDEX ix_notification_due ON swiftdrop.notification_jobs(next_attempt_at) WHERE state IN ('PENDING','RETRYING');

-- Retain unpublished pre-integration events and upgrade their envelope using canonical history.
UPDATE swiftdrop.outbox_events e SET payload=jsonb_build_object(
    'eventId',coalesce(e.payload->>'eventId',e.id::text),'eventType','parcel.status.changed','schemaVersion',1,
    'occurredAt',h.occurred_at,'merchantId',b.merchant_id,'parcelId',b.id,'lifecycleId',l.id,
    'trackingNumber',b.tracking_number,'status',h.new_status,'sequence',h.sequence_no)
FROM swiftdrop.parcel_lifecycles l JOIN swiftdrop.parcel_bookings b ON b.id=l.booking_id
JOIN swiftdrop.parcel_status_history h ON h.lifecycle_id=l.id
WHERE e.aggregate_id=l.id AND e.published_at IS NULL AND NOT(e.payload ? 'schemaVersion')
  AND h.new_status=e.payload->>'newStatus';
