-- Fix M6 constraint: Allow parcels to be created before a courier is assigned
ALTER TABLE swiftdrop.parcel_lifecycles ALTER COLUMN assigned_courier_id DROP NOT NULL;

-- Store operational information required for courier assignments (M5)
CREATE TABLE swiftdrop.courier_profiles (
    user_id uuid PRIMARY KEY REFERENCES swiftdrop.users (id),
    vehicle_type varchar(50) NOT NULL,
    license_plate varchar(20),
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_courier_profiles_updated_at CHECK (updated_at >= created_at)
);

-- Reuse the trigger function from V7
CREATE TRIGGER courier_profiles_updated_at BEFORE UPDATE ON swiftdrop.courier_profiles
    FOR EACH ROW EXECUTE FUNCTION swiftdrop.lifecycle_touch_updated_at();

-- Track current and past courier assignments for workload monitoring and history (M5)
CREATE TABLE swiftdrop.parcel_assignments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id uuid NOT NULL REFERENCES swiftdrop.parcel_bookings (id),
    courier_id uuid NOT NULL REFERENCES swiftdrop.users (id),
    assigned_by uuid NOT NULL REFERENCES swiftdrop.users (id),
    is_active boolean NOT NULL DEFAULT true,
    assigned_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at timestamptz,
    revocation_reason varchar(200),
    CONSTRAINT ck_parcel_assignments_revoked CHECK (
        (is_active = true AND revoked_at IS NULL) OR
        (is_active = false AND revoked_at IS NOT NULL)
    )
);

-- Concurrency Protection: Prevent two concurrent operations from creating conflicting current assignments
CREATE UNIQUE INDEX uq_parcel_assignments_active 
    ON swiftdrop.parcel_assignments (booking_id) WHERE is_active = true;

-- Index to quickly calculate each courier's current active delivery workload
CREATE INDEX ix_parcel_assignments_courier_workload 
    ON swiftdrop.parcel_assignments (courier_id, assigned_at) WHERE is_active = true;