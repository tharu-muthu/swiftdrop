-- DEV ONLY: synthetic reference prices, not real company prices. No users or passwords.
-- Select your own development Neon branch and apply Flyway through V12 first.
-- Existing zone IDs are resolved by name. Existing postcode/rate configuration is preserved.
BEGIN;
INSERT INTO swiftdrop.zones(name) VALUES ('Colombo'),('Galle'),('Kandy') ON CONFLICT(name) DO NOTHING;

WITH mappings(postcode,zone_name) AS (VALUES
  ('00100','Colombo'),('00300','Colombo'),('00600','Colombo'),('80000','Galle'),('20000','Kandy'))
INSERT INTO swiftdrop.zone_postcodes(postcode,zone_id)
SELECT m.postcode,z.id FROM mappings m JOIN swiftdrop.zones z ON z.name=m.zone_name
ON CONFLICT(postcode) DO NOTHING;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM (VALUES ('00100','Colombo'),('00300','Colombo'),('00600','Colombo'),('80000','Galle'),('20000','Kandy')) m(postcode,zone_name)
    JOIN swiftdrop.zone_postcodes p ON p.postcode=m.postcode JOIN swiftdrop.zones z ON z.id=p.zone_id
    WHERE z.name<>m.zone_name
  ) THEN RAISE EXCEPTION 'Demo postcodes already have different zone mappings. Review them in Pricing management before seeding.'; END IF;
END $$;

INSERT INTO swiftdrop.rate_card_versions
  (pickup_zone_id,dropoff_zone_id,base_minor,included_kg,extra_kg_minor,
   size_surcharge_small,size_surcharge_medium,size_surcharge_large,size_surcharge_extra_large,effective_from)
SELECT pickup.id,dropoff.id,CASE WHEN pickup.id=dropoff.id THEN 35000 ELSE 45000 END,1,15000,
       0,10000,25000,40000,'2026-01-01T00:00:00Z'
FROM swiftdrop.zones pickup CROSS JOIN swiftdrop.zones dropoff
WHERE pickup.name IN ('Colombo','Galle','Kandy') AND dropoff.name IN ('Colombo','Galle','Kandy')
ON CONFLICT(pickup_zone_id,dropoff_zone_id,effective_from) DO NOTHING;
COMMIT;
