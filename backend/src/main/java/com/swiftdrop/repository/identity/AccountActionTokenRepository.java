package com.swiftdrop.repository.identity;
import com.swiftdrop.entity.identity.AccountActionToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AccountActionTokenRepository extends JpaRepository<AccountActionToken,UUID> {
    @org.springframework.data.jpa.repository.Query("select t.userId from AccountActionToken t where t.tokenHash=:hash")
    Optional<UUID> ownerOf(@org.springframework.data.repository.query.Param("hash") String hash);
    Optional<AccountActionToken> findByTokenHash(String hash);
    List<AccountActionToken> findByUserIdAndUsedAtIsNullAndRevokedAtIsNull(UUID userId);
}
