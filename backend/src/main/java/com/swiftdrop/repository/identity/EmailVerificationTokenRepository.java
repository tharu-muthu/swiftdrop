package com.swiftdrop.repository.identity;

import com.swiftdrop.entity.identity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    @Query("select t.userId from EmailVerificationToken t where t.tokenHash = :hash")
    Optional<UUID> ownerOf(@Param("hash") String hash);
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);
    List<EmailVerificationToken> findByUserIdAndUsedAtIsNullAndRevokedAtIsNull(UUID userId);
}
