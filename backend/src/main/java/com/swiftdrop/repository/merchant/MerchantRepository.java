package com.swiftdrop.repository.merchant;

import com.swiftdrop.entity.merchant.Merchant;
import com.swiftdrop.entity.merchant.MerchantApprovalStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {
    Optional<Merchant> findByOwnerUserId(UUID ownerUserId);
    boolean existsByOwnerUserId(UUID ownerUserId);
    Page<Merchant> findByApprovalStatus(MerchantApprovalStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Merchant m where m.id = :id")
    Optional<Merchant> findForUpdate(@Param("id") UUID id);
}