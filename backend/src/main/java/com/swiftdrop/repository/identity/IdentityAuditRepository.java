package com.swiftdrop.repository.identity;

import com.swiftdrop.entity.identity.IdentityAuditEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityAuditRepository extends JpaRepository<IdentityAuditEvent, UUID> {
    boolean existsByAction(String action);
}
