package com.swiftdrop.repository.identity;

import com.swiftdrop.entity.identity.RefreshToken;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    // Scalar projection avoids caching an unlocked session/token before acquiring locks.
    interface Owner { UUID getUserId(); UUID getSessionId(); }
    @Query(value = """
            select s.user_id as userId, s.id as sessionId from swiftdrop.refresh_tokens t
            join swiftdrop.auth_sessions s on s.id=t.session_id where t.token_hash=:hash
            """, nativeQuery = true)
    Optional<Owner> findOwner(@Param("hash") String hash);
    Optional<RefreshToken> findByTokenHash(String hash);
}
