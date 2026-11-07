package com.swiftdrop.entity.booking;

import com.swiftdrop.entity.identity.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.sql.Types;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;

/** M4 booking aggregate; delivery lifecycle is deliberately not represented here. */
@Entity
@Table(name = "parcel_bookings", schema = "swiftdrop")
public class ParcelBooking extends UuidEntity {
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "tracking_number", nullable = false, updatable = false, length = 40)
    private String trackingNumber;

    @Column(name = "booking_state", nullable = false, length = 24)
    private String bookingState;

    @Column(name = "receipt_state", nullable = false, length = 24)
    private String receiptState;

    @Column(name = "pickup_address_snapshot", nullable = false, updatable = false, length = 500)
    private String pickupAddressSnapshot;

    @Column(name = "dropoff_address_snapshot", nullable = false, updatable = false, length = 500)
    private String dropoffAddressSnapshot;

    @Column(name = "parcel_description", nullable = false, updatable = false, length = 200)
    private String parcelDescription;

    @Column(name = "parcel_weight_grams", nullable = false, updatable = false)
    private int parcelWeightGrams;

    @Column(name = "quoted_amount_minor", updatable = false)
    private Integer quotedAmountMinor;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "currency_code", columnDefinition = "char(3)", updatable = false)
    private String currencyCode;

    @Column(name = "recipient_name", length = 120, updatable = false)
    private String recipientName;

    @Column(name = "recipient_phone", length = 32, updatable = false)
    private String recipientPhone;

    @Column(name = "pickup_city", length = 100, updatable = false)
    private String pickupCity;

    @Column(name = "delivery_city", length = 100, updatable = false)
    private String deliveryCity;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected ParcelBooking() { }

    public UUID getCustomerId() { return customerId; }
    public String getTrackingNumber() { return trackingNumber; }
    public String getBookingState() { return bookingState; }
    public String getReceiptState() { return receiptState; }
    public String getPickupAddressSnapshot() { return pickupAddressSnapshot; }
    public String getDropoffAddressSnapshot() { return dropoffAddressSnapshot; }
    public String getParcelDescription() { return parcelDescription; }
    public int getParcelWeightGrams() { return parcelWeightGrams; }
    public String getRecipientName() { return recipientName; }
    public String getRecipientPhone() { return recipientPhone; }
    public String getPickupCity() { return pickupCity; }
    public String getDeliveryCity() { return deliveryCity; }
    public Integer getQuotedAmountMinor() { return quotedAmountMinor; }
    public String getCurrencyCode() { return currencyCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
