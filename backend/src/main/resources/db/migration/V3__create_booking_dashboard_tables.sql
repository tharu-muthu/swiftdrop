-- M4 owns the booking record and receipt workflow. Parcel lifecycle/status remains M6-owned.
CREATE TABLE swiftdrop.parcel_bookings (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id uuid NOT NULL REFERENCES swiftdrop.users (id),
    tracking_number varchar(40) NOT NULL,
    booking_state varchar(24) NOT NULL DEFAULT 'CREATED',
    receipt_state varchar(24) NOT NULL DEFAULT 'PENDING',
    pickup_address_snapshot varchar(500) NOT NULL,
    dropoff_address_snapshot varchar(500) NOT NULL,
    parcel_description varchar(200) NOT NULL,
    parcel_weight_grams integer NOT NULL,
    quoted_amount_minor integer,
    currency_code char(3),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_parcel_bookings_tracking_number UNIQUE (tracking_number),
    CONSTRAINT ck_parcel_bookings_tracking_number CHECK (tracking_number ~ '^SDP-[A-Z0-9]{6,32}$'),
    CONSTRAINT ck_parcel_bookings_state CHECK (booking_state IN ('CREATED', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT ck_parcel_bookings_receipt_state CHECK (receipt_state IN ('PENDING', 'ISSUED')),
    CONSTRAINT ck_parcel_bookings_addresses CHECK (
        length(btrim(pickup_address_snapshot)) > 0 AND length(btrim(dropoff_address_snapshot)) > 0
    ),
    CONSTRAINT ck_parcel_bookings_description CHECK (length(btrim(parcel_description)) > 0),
    CONSTRAINT ck_parcel_bookings_weight CHECK (parcel_weight_grams > 0),
    CONSTRAINT ck_parcel_bookings_quote_amount CHECK (quoted_amount_minor IS NULL OR quoted_amount_minor >= 0),
    CONSTRAINT ck_parcel_bookings_currency CHECK (
        (quoted_amount_minor IS NULL AND currency_code IS NULL) OR
        (quoted_amount_minor IS NOT NULL AND currency_code ~ '^[A-Z]{3}$')
    ),
    CONSTRAINT ck_parcel_bookings_updated_at CHECK (updated_at >= created_at)
);

CREATE INDEX ix_parcel_bookings_customer_created ON swiftdrop.parcel_bookings (customer_id, created_at DESC);
CREATE INDEX ix_parcel_bookings_customer_active ON swiftdrop.parcel_bookings (customer_id, booking_state)
    WHERE booking_state IN ('CREATED', 'CONFIRMED');

CREATE FUNCTION swiftdrop.booking_touch_updated_at() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := clock_timestamp();
    RETURN NEW;
END;
$$;
CREATE TRIGGER parcel_bookings_updated_at BEFORE UPDATE ON swiftdrop.parcel_bookings
    FOR EACH ROW EXECUTE FUNCTION swiftdrop.booking_touch_updated_at();

COMMENT ON TABLE swiftdrop.parcel_bookings IS
    'M4 booking ownership and immutable address snapshots. M6 owns canonical delivery lifecycle/status.';
COMMENT ON COLUMN swiftdrop.parcel_bookings.booking_state IS
    'M4 administrative booking state only; it is not parcel delivery status.';
