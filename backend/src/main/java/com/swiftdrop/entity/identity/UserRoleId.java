package com.swiftdrop.entity.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class UserRoleId implements Serializable {
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    protected UserRoleId() { }
    public UserRoleId(UUID userId, UUID roleId) {
        this.userId = userId;
        this.roleId = roleId;
    }
    public UUID getUserId() { return userId; }
    public UUID getRoleId() { return roleId; }

    @Override
    public boolean equals(Object other) {
        return other instanceof UserRoleId that
                && Objects.equals(userId, that.userId) && Objects.equals(roleId, that.roleId);
    }
    @Override
    public int hashCode() { return Objects.hash(userId, roleId); }
}
