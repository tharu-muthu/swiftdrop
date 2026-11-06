package com.swiftdrop.repository.merchant;

import com.swiftdrop.entity.merchant.Merchant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {
    Optional<Merchant> findByOwnerUserId(UUID ownerUserId);
    boolean existsByOwnerUserId(UUID ownerUserId);
}