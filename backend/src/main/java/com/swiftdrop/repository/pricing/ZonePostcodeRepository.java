package com.swiftdrop.repository.pricing;

import com.swiftdrop.entity.pricing.ZonePostcode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZonePostcodeRepository extends JpaRepository<ZonePostcode, UUID> {
    Optional<ZonePostcode> findByPostcode(String postcode);
    boolean existsByPostcode(String postcode);
}