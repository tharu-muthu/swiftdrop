package com.swiftdrop.repository.identity;

import com.swiftdrop.entity.identity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Query("select t.userId from PasswordResetToken t where t.tokenHash = :hash")
    Optional<UUID> ownerOf(@Param("hash") String hash);

    List<PasswordResetToken> findByUserIdAndUsedAtIsNullAndRevokedAtIsNull(UUID userId);
}
