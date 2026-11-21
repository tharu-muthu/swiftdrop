package com.swiftdrop.repository.identity;

import com.swiftdrop.entity.identity.AuthSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    List<AuthSession> findByUserIdAndRevokedAtIsNull(UUID userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.id = :id")
    Optional<AuthSession> lockById(@Param("id") UUID id);
}
