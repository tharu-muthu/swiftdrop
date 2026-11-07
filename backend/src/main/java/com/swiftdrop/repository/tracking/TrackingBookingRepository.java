package com.swiftdrop.repository.tracking;

import com.swiftdrop.entity.booking.ParcelBooking;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackingBookingRepository extends JpaRepository<ParcelBooking, UUID> {
    Optional<ParcelBooking> findByTrackingNumber(String trackingNumber);
}
