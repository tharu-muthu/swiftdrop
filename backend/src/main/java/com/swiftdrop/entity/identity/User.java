package com.swiftdrop.entity.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import java.time.Instant;

@Entity
@Table(name = "users", schema = "swiftdrop")
public class User extends CreatedEntity {
    @Column(nullable = false, length = 320)
    private String email;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "email_normalized", nullable = false, length = 320, insertable = false, updatable = false)
    private String emailNormalized;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(length = 32)
    private String phone;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "authorization_version", nullable = false)
    private long authorizationVersion;

    @Version
    @Column(nullable = false)
    private long version;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected User() { }

    public static User registerCustomer(String fullName, String email, String passwordHash) {
        var user = new User();
        user.fullName = fullName;
        user.email = email;
        user.passwordHash = passwordHash;
        return user;
    }

    public static User registerGoogleCustomer(String fullName, String email) {
        var user = new User();
        user.fullName = fullName;
        user.email = email;
        // passwordHash remains null — Google-only account
        return user;
    }


    public String getEmail() { return email; }
    public String getEmailNormalized() { return emailNormalized; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActive() { return active; }
    public long getAuthorizationVersion() { return authorizationVersion; }
    public long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
}
