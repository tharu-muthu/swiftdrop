package com.swiftdrop.repository.identity;

import com.swiftdrop.entity.identity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByName(String name);
    @Query(value = "select r.name from swiftdrop.roles r join swiftdrop.user_roles ur on ur.role_id=r.id where ur.user_id=:id", nativeQuery = true)
    Set<String> namesForUser(@Param("id") UUID id);
}
