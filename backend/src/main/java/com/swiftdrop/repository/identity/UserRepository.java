package com.swiftdrop.repository.identity;

import com.swiftdrop.entity.identity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface UserRepository extends JpaRepository<User, UUID> {
    @Query(value = """
            select u.id from swiftdrop.users u
            join swiftdrop.user_roles ur on ur.user_id=u.id
            join swiftdrop.roles r on r.id=ur.role_id
            where u.id in (:ids) and u.active and u.email_verified_at is not null and r.name='COURIER'
            """, nativeQuery = true)
    java.util.Set<UUID> eligibleCourierIds(@Param("ids") java.util.Collection<UUID> ids);
    // Same PostgreSQL expression/collation as V2's generated unique column.
    @Query(value = "select lower(btrim(cast(:email as text)))", nativeQuery = true)
    String normalizeEmail(@Param("email") String email);
    Optional<User> findByEmailNormalized(String email);
    interface LoginCredential { UUID getId(); String getPasswordHash(); }
    @Query(value = "select id, password_hash as passwordHash from swiftdrop.users where email_normalized=:email", nativeQuery = true)
    Optional<LoginCredential> credentialForEmail(@Param("email") String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> lockById(@Param("id") UUID id);
    @Query("select u from User u where :query = '' or lower(u.fullName) like concat('%', :query, '%') escape '!' or u.emailNormalized like concat('%', :query, '%') escape '!'")
    Page<User> searchForAdmin(@Param("query") String query, Pageable pageable);
}
