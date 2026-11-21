package com.swiftdrop.entity.pricing;

import com.swiftdrop.entity.identity.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "zones", schema = "swiftdrop")
public class Zone extends UuidEntity {
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Zone() { }

    public Zone(String name) { this.name = name; }

    public String getName() { return name; }
    public boolean isActive() { return active; }
}
