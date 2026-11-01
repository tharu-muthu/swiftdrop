-- Flyway creates the single swiftdrop schema before running this migration.
-- No identity, parcel or other business tables belong in the foundation.
COMMENT ON SCHEMA swiftdrop IS 'SwiftDrop shared application schema; managed by Flyway';
