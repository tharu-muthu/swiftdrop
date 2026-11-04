package com.swiftdrop.entity.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "roles", schema = "swiftdrop")
public class Role extends CreatedEntity {
    @Column(nullable = false, length = 32)
    private String name;

    protected Role() { }

    public String getName() { return name; }
}
