package com.swiftdrop.entity.identity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import java.time.Instant;

@Entity
@Table(name = "user_roles", schema = "swiftdrop")
public class UserRole {
    @EmbeddedId
    private UserRoleId id;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected UserRole() { }
    public UserRole(java.util.UUID userId, java.util.UUID roleId) {
        this.id = new UserRoleId(userId, roleId);
    }
    public UserRoleId getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
}
