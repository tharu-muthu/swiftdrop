package com.swiftdrop.entity.pricing;

import com.swiftdrop.entity.identity.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "zone_postcodes", schema = "swiftdrop")
public class ZonePostcode extends UuidEntity {
    @Column(name = "postcode", nullable = false, length = 10)
    private String postcode;

    @Column(name = "zone_id", nullable = false)
    private UUID zoneId;

    protected ZonePostcode() { }

    public ZonePostcode(String postcode, UUID zoneId) {
        this.postcode = postcode;
        this.zoneId = zoneId;
    }

    public String getPostcode() { return postcode; }
    public UUID getZoneId() { return zoneId; }
}
