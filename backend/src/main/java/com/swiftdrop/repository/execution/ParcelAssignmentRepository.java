package com.swiftdrop.repository.execution;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.swiftdrop.entity.execution.ParcelAssignment;

@Repository
public interface ParcelAssignmentRepository extends JpaRepository<ParcelAssignment, UUID> {
    
    // Finds the current active assignment for a specific parcel
    Optional<ParcelAssignment> findByBookingIdAndIsActiveTrue(UUID bookingId);
    
    // Calculates a courier's current active delivery workload
    long countByCourierIdAndIsActiveTrue(UUID courierId);
}