ALTER TABLE swiftdrop.parcel_bookings ADD COLUMN external_reference varchar(100);
CREATE UNIQUE INDEX uq_booking_merchant_external_reference ON swiftdrop.parcel_bookings(merchant_id,external_reference)
    WHERE merchant_id IS NOT NULL AND external_reference IS NOT NULL;
