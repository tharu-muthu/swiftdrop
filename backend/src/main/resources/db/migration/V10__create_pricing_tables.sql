-- M3: zones, postcode mapping, versioned rate cards, saved quotes.
-- Money is LKR minor units (BIGINT); weight is integer grams.
-- Requires V2 (users) and V6 (merchants). V9 (email verification) must be applied before V10.

CREATE TABLE zones (
    id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name   VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_zones_name UNIQUE (name)
);

CREATE TABLE zone_postcodes (
    id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    postcode VARCHAR(10) NOT NULL,
    zone_id  UUID NOT NULL REFERENCES zones (id),
    CONSTRAINT uq_zone_postcodes_postcode UNIQUE (postcode)
);
CREATE INDEX ix_zone_postcodes_zone ON zone_postcodes (zone_id);

CREATE TABLE rate_card_versions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pickup_zone_id   UUID NOT NULL REFERENCES zones (id),
    dropoff_zone_id  UUID NOT NULL REFERENCES zones (id),
    currency         VARCHAR(3) NOT NULL DEFAULT 'LKR',
    base_minor       BIGINT NOT NULL,
    included_kg      INT NOT NULL,
    extra_kg_minor   BIGINT NOT NULL,
    size_surcharge_s BIGINT NOT NULL DEFAULT 0,
    size_surcharge_m BIGINT NOT NULL DEFAULT 0,
    size_surcharge_l BIGINT NOT NULL DEFAULT 0,
    effective_from   TIMESTAMPTZ NOT NULL,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_rate_cards_route_effective UNIQUE (pickup_zone_id, dropoff_zone_id, effective_from),
    CONSTRAINT ck_rate_cards_amounts CHECK (
        base_minor >= 0 AND included_kg >= 0 AND extra_kg_minor >= 0 AND
        size_surcharge_s >= 0 AND size_surcharge_m >= 0 AND size_surcharge_l >= 0)
);
CREATE INDEX ix_rate_cards_route ON rate_card_versions (pickup_zone_id, dropoff_zone_id, effective_from DESC);

CREATE TABLE quotations (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id          UUID REFERENCES users (id),
    merchant_id          UUID REFERENCES merchants (id),
    rate_version_id      UUID NOT NULL REFERENCES rate_card_versions (id),
    input_hash           VARCHAR(64) NOT NULL,
    weight_grams         INT NOT NULL,
    size                 VARCHAR(1) NOT NULL,
    currency             VARCHAR(3) NOT NULL,
    base_minor           BIGINT NOT NULL,
    extra_weight_minor   BIGINT NOT NULL,
    size_surcharge_minor BIGINT NOT NULL,
    amount_minor         BIGINT NOT NULL,
    expires_at           TIMESTAMPTZ NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_quotations_one_owner CHECK ((customer_id IS NULL) <> (merchant_id IS NULL)),
    CONSTRAINT ck_quotations_size CHECK (size IN ('S', 'M', 'L')),
    CONSTRAINT ck_quotations_weight CHECK (weight_grams > 0),
    CONSTRAINT ck_quotations_hash CHECK (input_hash ~ '^[0-9a-f]{64}$')
);
CREATE INDEX ix_quotations_customer ON quotations (customer_id, created_at DESC);
CREATE INDEX ix_quotations_merchant ON quotations (merchant_id, created_at DESC);
