package com.swiftdrop.repository.execution;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.swiftdrop.entity.execution.CourierProfile;

@Repository
public interface CourierProfileRepository extends JpaRepository<CourierProfile, UUID> {
    
    // Identifies active couriers who are eligible to receive delivery tasks
    List<CourierProfile> findAllByIsActiveTrue();
}