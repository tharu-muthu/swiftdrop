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

    @Column(name = "size_surcharge_small", nullable = false)
    private long sizeSurchargeSmall;

    @Column(name = "size_surcharge_medium", nullable = false)
    private long sizeSurchargeMedium;

    @Column(name = "size_surcharge_large", nullable = false)
    private long sizeSurchargeLarge;

    @Column(name = "size_surcharge_extra_large", nullable = false)
    private long sizeSurchargeExtraLarge;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected RateCardVersion() { }

    public RateCardVersion(UUID pickupZoneId, UUID dropoffZoneId, long baseMinor, int includedKg,
            long extraKgMinor, long sizeSurchargeSmall, long sizeSurchargeMedium, long sizeSurchargeLarge,
            long sizeSurchargeExtraLarge, Instant effectiveFrom) {
        this.pickupZoneId = pickupZoneId;
        this.dropoffZoneId = dropoffZoneId;
        this.baseMinor = baseMinor;
        this.includedKg = includedKg;
        this.extraKgMinor = extraKgMinor;
        this.sizeSurchargeSmall = sizeSurchargeSmall;
        this.sizeSurchargeMedium = sizeSurchargeMedium;
        this.sizeSurchargeLarge = sizeSurchargeLarge;
        this.sizeSurchargeExtraLarge = sizeSurchargeExtraLarge;
        this.effectiveFrom = effectiveFrom;
    }

    public long surchargeFor(ParcelSize size) {
        return switch (size) {
            case SMALL -> sizeSurchargeSmall;
            case MEDIUM -> sizeSurchargeMedium;
            case LARGE -> sizeSurchargeLarge;
            case EXTRA_LARGE -> sizeSurchargeExtraLarge;
        };
    }

    public UUID getPickupZoneId() { return pickupZoneId; }
    public UUID getDropoffZoneId() { return dropoffZoneId; }
    public String getCurrency() { return currency; }
    public long getBaseMinor() { return baseMinor; }
    public int getIncludedKg() { return includedKg; }
    public long getExtraKgMinor() { return extraKgMinor; }
    public long getSizeSurchargeSmall() { return sizeSurchargeSmall; }
    public long getSizeSurchargeMedium() { return sizeSurchargeMedium; }
    public long getSizeSurchargeLarge() { return sizeSurchargeLarge; }
    public long getSizeSurchargeExtraLarge() { return sizeSurchargeExtraLarge; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public boolean isActive() { return active; }
}