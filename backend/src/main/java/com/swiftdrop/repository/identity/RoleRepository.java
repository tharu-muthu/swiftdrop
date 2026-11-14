package com.swiftdrop.repository.identity;

import com.swiftdrop.entity.identity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByName(String name);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Role r where r.name = :name")
    Optional<Role> lockByName(@Param("name") String name);
    @Query(value = "select count(*) from swiftdrop.user_roles ur join swiftdrop.roles r on r.id=ur.role_id join swiftdrop.users u on u.id=ur.user_id where r.name='DISPATCHER_ADMIN'", nativeQuery = true)
    long countAdministrators();
    @Query(value = "select r.name from swiftdrop.roles r join swiftdrop.user_roles ur on ur.role_id=r.id where ur.user_id=:id", nativeQuery = true)
    Set<String> namesForUser(@Param("id") UUID id);
}
