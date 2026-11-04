package com.swiftdrop.repository.identity;

import com.swiftdrop.entity.identity.OAuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, UUID> {
    Optional<OAuthAccount> findByProviderAndProviderSubject(String provider, String providerSubject);
    boolean existsByUserIdAndProvider(UUID userId, String provider);
}

