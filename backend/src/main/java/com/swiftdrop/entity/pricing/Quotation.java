package com.swiftdrop.entity.pricing;

import com.swiftdrop.entity.identity.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "quotations", schema = "swiftdrop")
public class Quotation extends UuidEntity {
    @Column(name = "customer_id", updatable = false)
    private UUID customerId;

    @Column(name = "merchant_id", updatable = false)
    private UUID merchantId;

    @Column(name = "rate_version_id", nullable = false, updatable = false)
    private UUID rateVersionId;

    @Column(name = "input_hash", nullable = false, updatable = false, length = 64)
    private String inputHash;

    @Column(name = "weight_grams", nullable = false, updatable = false)
    private int weightGrams;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "size", nullable = false, updatable = false, length = 12)
    private ParcelSize size;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "base_minor", nullable = false, updatable = false)
    private long baseMinor;

    @Column(name = "extra_weight_minor", nullable = false, updatable = false)
    private long extraWeightMinor;

    @Column(name = "size_surcharge_minor", nullable = false, updatable = false)
    private long sizeSurchargeMinor;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Quotation() { }

    public Quotation(UUID ownerId, boolean merchantOwner, UUID rateVersionId, String inputHash,
            int weightGrams, ParcelSize size, String currency, long baseMinor, long extraWeightMinor,
            long sizeSurchargeMinor, long amountMinor, Instant expiresAt) {
        if (merchantOwner) { this.merchantId = ownerId; } else { this.customerId = ownerId; }
        this.rateVersionId = rateVersionId;
        this.inputHash = inputHash;
        this.weightGrams = weightGrams;
        this.size = size;
        this.currency = currency;
        this.baseMinor = baseMinor;
        this.extraWeightMinor = extraWeightMinor;
        this.sizeSurchargeMinor = sizeSurchargeMinor;
        this.amountMinor = amountMinor;
        this.expiresAt = expiresAt;
    }

    public boolean isOwnedBy(UUID ownerId, boolean merchantOwner) {
        UUID mine = merchantOwner ? merchantId : customerId;
        return mine != null && mine.equals(ownerId);
    }

    public boolean isExpiredAt(Instant now) { return !now.isBefore(expiresAt); }

    public UUID getRateVersionId() { return rateVersionId; }
    public String getInputHash() { return inputHash; }
    public String getCurrency() { return currency; }
    public long getBaseMinor() { return baseMinor; }
    public long getExtraWeightMinor() { return extraWeightMinor; }
    public long getSizeSurchargeMinor() { return sizeSurchargeMinor; }
    public long getAmountMinor() { return amountMinor; }
    public Instant getExpiresAt() { return expiresAt; }
}
