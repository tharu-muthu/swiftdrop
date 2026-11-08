package com.swiftdrop.repository.merchant;

import com.swiftdrop.entity.merchant.ApiCredential;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiCredentialRepository extends JpaRepository<ApiCredential, UUID> {
    List<ApiCredential> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
    Optional<ApiCredential> findByIdAndMerchantId(UUID id, UUID merchantId);
    long countByMerchantIdAndRevokedAtIsNull(UUID merchantId);
    boolean existsByKeyPrefix(String keyPrefix);
}