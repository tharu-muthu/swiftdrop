package com.swiftdrop.repository.execution;

import com.swiftdrop.entity.execution.DeliveryProof;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface DeliveryProofRepository extends JpaRepository<DeliveryProof, UUID> { }
