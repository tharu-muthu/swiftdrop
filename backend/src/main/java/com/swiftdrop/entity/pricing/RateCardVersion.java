package com.swiftdrop.entity.pricing;

import com.swiftdrop.entity.identity.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rate_card_versions", schema = "swiftdrop")
public class RateCardVersion extends UuidEntity {
    @Column(name = "pickup_zone_id", nullable = false, updatable = false)
    private UUID pickupZoneId;

    @Column(name = "dropoff_zone_id", nullable = false, updatable = false)
    private UUID dropoffZoneId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "LKR";

    @Column(name = "base_minor", nullable = false)
    private long baseMinor;

    @Column(name = "included_kg", nullable = false)
    private int includedKg;

    @Column(name = "extra_kg_minor", nullable = false)
    private long extraKgMinor;

    @Column(name = "size_surcharge_s", nullable = false)
    private long sizeSurchargeS;

    @Column(name = "size_surcharge_m", nullable = false)
    private long sizeSurchargeM;

    @Column(name = "size_surcharge_l", nullable = false)
    private long sizeSurchargeL;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected RateCardVersion() { }

    public RateCardVersion(UUID pickupZoneId, UUID dropoffZoneId, long baseMinor, int includedKg,
            long extraKgMinor, long sizeSurchargeS, long sizeSurchargeM, long sizeSurchargeL,
            Instant effectiveFrom) {
        this.pickupZoneId = pickupZoneId;
        this.dropoffZoneId = dropoffZoneId;
        this.baseMinor = baseMinor;
        this.includedKg = includedKg;
        this.extraKgMinor = extraKgMinor;
        this.sizeSurchargeS = sizeSurchargeS;
        this.sizeSurchargeM = sizeSurchargeM;
        this.sizeSurchargeL = sizeSurchargeL;
        this.effectiveFrom = effectiveFrom;
    }

    public long surchargeFor(ParcelSize size) {
        return switch (size) {
            case S -> sizeSurchargeS;
            case M -> sizeSurchargeM;
            case L -> sizeSurchargeL;
        };
    }

    public UUID getPickupZoneId() { return pickupZoneId; }
    public UUID getDropoffZoneId() { return dropoffZoneId; }
    public String getCurrency() { return currency; }
    public long getBaseMinor() { return baseMinor; }
    public int getIncludedKg() { return includedKg; }
    public long getExtraKgMinor() { return extraKgMinor; }
    public long getSizeSurchargeS() { return sizeSurchargeS; }
    public long getSizeSurchargeM() { return sizeSurchargeM; }
    public long getSizeSurchargeL() { return sizeSurchargeL; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public boolean isActive() { return active; }
}
