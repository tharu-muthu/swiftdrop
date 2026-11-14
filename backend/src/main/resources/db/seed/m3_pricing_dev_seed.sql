-- DEV ONLY demo data (synthetic rates, not real company prices). Safe to re-run.
-- Run manually in the Neon SQL editor AFTER V10 has been applied.
INSERT INTO swiftdrop.zones (id, name) VALUES
  ('11111111-1111-4111-8111-111111111111', 'Colombo'),
  ('22222222-2222-4222-8222-222222222222', 'Galle')
ON CONFLICT (name) DO NOTHING;

INSERT INTO swiftdrop.zone_postcodes (postcode, zone_id) VALUES
  ('00100', '11111111-1111-4111-8111-111111111111'),
  ('00300', '11111111-1111-4111-8111-111111111111'),
  ('80000', '22222222-2222-4222-8222-222222222222')
ON CONFLICT (postcode) DO NOTHING;

INSERT INTO swiftdrop.rate_card_versions
 (id, pickup_zone_id, dropoff_zone_id, base_minor, included_kg, extra_kg_minor,
  size_surcharge_small, size_surcharge_medium, size_surcharge_large, size_surcharge_extra_large, effective_from)
VALUES
 ('aaaaaaaa-0000-4000-8000-000000000001','11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222',
  45000, 1, 15000, 0, 10000, 25000, 40000, '2026-01-01T00:00:00Z'),
 ('aaaaaaaa-0000-4000-8000-000000000002','22222222-2222-4222-8222-222222222222','11111111-1111-4111-8111-111111111111',
  45000, 1, 15000, 0, 10000, 25000, 40000, '2026-01-01T00:00:00Z')
ON CONFLICT (id) DO NOTHING;