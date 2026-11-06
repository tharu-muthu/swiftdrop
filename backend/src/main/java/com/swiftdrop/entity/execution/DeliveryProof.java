package com.swiftdrop.entity.execution;

import com.swiftdrop.entity.identity.CreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "delivery_proofs", schema = "swiftdrop")
public class DeliveryProof extends CreatedEntity {
    @Column(name = "history_id", nullable = false, updatable = false, unique = true)
    private UUID historyId;
    @Column(name = "recipient_name", nullable = false, length = 200, updatable = false)
    private String recipientName;
    protected DeliveryProof() { }
    public static DeliveryProof create(UUID historyId, String recipientName) {
        var proof = new DeliveryProof();
        proof.historyId = historyId; proof.recipientName = recipientName;
        return proof;
    }
}
