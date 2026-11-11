package com.swiftdrop.repository.pricing;

import com.swiftdrop.entity.pricing.Zone;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {
    boolean existsByName(String name);
}