package com.swiftdrop.entity.merchant;

import com.swiftdrop.entity.identity.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "merchants", schema = "swiftdrop")
public class Merchant extends UuidEntity {
    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private UUID ownerUserId;

    @Column(name = "business_name", nullable = false, length = 150)
    private String businessName;

    @Column(name = "contact_email", nullable = false, length = 254)
    private String contactEmail;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "business_address", length = 300)
    private String businessAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private MerchantApprovalStatus approvalStatus;

    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Merchant() { }

    public static Merchant register(UUID ownerUserId, String businessName, String contactEmail,
            String contactPhone, String businessAddress) {
        var merchant = new Merchant();
        merchant.ownerUserId = ownerUserId;
        merchant.businessName = businessName;
        merchant.contactEmail = contactEmail;
        merchant.contactPhone = contactPhone;
        merchant.businessAddress = businessAddress;
        merchant.approvalStatus = MerchantApprovalStatus.PENDING;
        return merchant;
    }

    public UUID getOwnerUserId() { return ownerUserId; }
    public String getBusinessName() { return businessName; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public String getBusinessAddress() { return businessAddress; }
    public MerchantApprovalStatus getApprovalStatus() { return approvalStatus; }
    public String getReviewReason() { return reviewReason; }
    public UUID getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}