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
    interface AccountDisplay { UUID getId(); String getFullName(); String getEmail(); }
    @Query("select u.id as id,u.fullName as fullName,u.email as email from User u where u.id in :ids")
    java.util.List<AccountDisplay> displayForIds(@Param("ids") java.util.Collection<UUID> ids);
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
    @Query(value = """
            select u.* from swiftdrop.users u
            where (:query = '' or lower(u.full_name) like concat('%', :query, '%') escape '!'
                    or u.email_normalized like concat('%', :query, '%') escape '!')
              and (:role = '' or exists (
                    select 1 from swiftdrop.user_roles ur join swiftdrop.roles r on r.id=ur.role_id
                    where ur.user_id=u.id and r.name=:role))
            order by u.created_at desc,u.id
            """, countQuery = """
            select count(*) from swiftdrop.users u
            where (:query = '' or lower(u.full_name) like concat('%', :query, '%') escape '!'
                    or u.email_normalized like concat('%', :query, '%') escape '!')
              and (:role = '' or exists (
                    select 1 from swiftdrop.user_roles ur join swiftdrop.roles r on r.id=ur.role_id
                    where ur.user_id=u.id and r.name=:role))
            """, nativeQuery = true)
    Page<User> searchForAdmin(@Param("query") String query, @Param("role") String role, Pageable pageable);
}
