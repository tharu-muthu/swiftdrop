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
    @Column(name = "customer_id", updatable = false)
    private UUID customerId;

    @Column(name = "merchant_id", updatable = false) private UUID merchantId;
    @Column(name = "quote_id", updatable = false) private UUID quoteId;
    @Column(name = "idempotency_key", length = 100, updatable = false) private String idempotencyKey;
    @Column(name = "request_hash", length = 64, updatable = false) private String requestHash;
    @Column(name = "sender_name", length = 120, updatable = false) private String senderName;
    @Column(name = "sender_phone", length = 32, updatable = false) private String senderPhone;
    @Column(name = "pickup_postal_code", length = 10, updatable = false) private String pickupPostalCode;
    @Column(name = "delivery_postal_code", length = 10, updatable = false) private String deliveryPostalCode;
    @Column(name = "parcel_size", length = 24, updatable = false) private String parcelSize;
    @Column(name = "external_reference", length = 100, updatable = false) private String externalReference;

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

    public static ParcelBooking create(UUID customerId, UUID merchantId, String key, String hash,
            com.swiftdrop.dto.booking.BookingCreateRequest request, com.swiftdrop.dto.pricing.QuoteResponse quote) {
        var booking = new ParcelBooking();
        booking.customerId = customerId; booking.merchantId = merchantId; booking.quoteId = quote.quoteId();
        booking.idempotencyKey = key; booking.requestHash = hash;
        booking.trackingNumber = "SDP-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
        booking.bookingState = "CONFIRMED"; booking.receiptState = "PENDING";
        booking.senderName = request.pickup().fullName().trim(); booking.senderPhone = request.pickup().contactNumber();
        booking.pickupAddressSnapshot = request.pickup().address().trim();
        booking.dropoffAddressSnapshot = request.recipient().address().trim();
        booking.pickupCity = request.pickup().city().trim(); booking.deliveryCity = request.recipient().city().trim();
        booking.pickupPostalCode = request.pickup().postalCode(); booking.deliveryPostalCode = request.recipient().postalCode();
        booking.recipientName = request.recipient().fullName().trim(); booking.recipientPhone = request.recipient().contactNumber();
        booking.parcelDescription = request.parcel().description().trim(); booking.parcelWeightGrams = request.parcel().weightGrams();
        booking.parcelSize = request.parcel().size(); booking.quotedAmountMinor = Math.toIntExact(quote.amountMinor());
        booking.currencyCode = quote.currency();
        booking.externalReference=request.externalReference()==null||request.externalReference().isBlank()?null:request.externalReference().trim();
        return booking;
    }
    public UUID getMerchantId() { return merchantId; }
    public String getRequestHash() { return requestHash; }
    public String getExternalReference() { return externalReference; }
    public String getSenderName() { return senderName; }
    public String getSenderPhone() { return senderPhone; }
    public String getPickupPostalCode() { return pickupPostalCode; }
    public String getDeliveryPostalCode() { return deliveryPostalCode; }
    public String getParcelSize() { return parcelSize; }
    public void issueReceipt() { receiptState = "ISSUED"; }

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
