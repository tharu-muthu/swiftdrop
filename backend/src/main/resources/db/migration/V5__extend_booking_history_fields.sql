-- M4 history display fields. These remain booking snapshots, not M6 lifecycle state.
ALTER TABLE swiftdrop.parcel_bookings
    ADD COLUMN recipient_name varchar(120),
    ADD COLUMN recipient_phone varchar(32),
    ADD COLUMN pickup_city varchar(100),
    ADD COLUMN delivery_city varchar(100);

CREATE INDEX ix_parcel_bookings_customer_recipient
    ON swiftdrop.parcel_bookings (customer_id, recipient_name);

COMMENT ON COLUMN swiftdrop.parcel_bookings.recipient_name IS
    'M4 recipient snapshot for customer-owned booking history.';
COMMENT ON COLUMN swiftdrop.parcel_bookings.pickup_city IS
    'M4 pickup city snapshot; nullable for bookings created before this field existed.';
