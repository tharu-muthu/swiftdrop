package com.swiftdrop.repository.pricing;

import com.swiftdrop.entity.pricing.RateCardVersion;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RateCardRepository extends JpaRepository<RateCardVersion, UUID> {

    /** The newest active rate card for a route that has already started. */
    @Query("""
            select r from RateCardVersion r
            where r.pickupZoneId = :pickup and r.dropoffZoneId = :dropoff
              and r.active = true and r.effectiveFrom <= :at
            order by r.effectiveFrom desc
            limit 1
            """)
    Optional<RateCardVersion> findCurrent(@Param("pickup") UUID pickupZoneId,
            @Param("dropoff") UUID dropoffZoneId, @Param("at") Instant at);

    boolean existsByPickupZoneIdAndDropoffZoneIdAndEffectiveFrom(UUID pickupZoneId, UUID dropoffZoneId,
            Instant effectiveFrom);
}