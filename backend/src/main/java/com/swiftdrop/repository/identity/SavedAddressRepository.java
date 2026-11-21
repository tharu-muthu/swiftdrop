package com.swiftdrop.repository.identity;
import com.swiftdrop.entity.identity.SavedAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SavedAddressRepository extends JpaRepository<SavedAddress,UUID>{
    List<SavedAddress> findByUserIdOrderByDefaultPickupDescCreatedAtAsc(UUID userId);
    Optional<SavedAddress> findByIdAndUserId(UUID id,UUID userId);
    long countByUserId(UUID userId);
}
