package com.swiftdrop.service.execution;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.entity.booking.ParcelBooking;
import com.swiftdrop.entity.execution.CourierProfile;
import com.swiftdrop.entity.execution.ParcelAssignment;
import com.swiftdrop.entity.identity.User;
import com.swiftdrop.repository.booking.ParcelBookingRepository;
import com.swiftdrop.repository.execution.CourierProfileRepository;
import com.swiftdrop.repository.execution.ParcelAssignmentRepository;
import com.swiftdrop.repository.execution.ParcelLifecycleRepository;
import com.swiftdrop.repository.identity.UserRepository;

@Service
public class CourierAssignmentService {

    private final ParcelAssignmentRepository assignmentRepository;
    private final CourierProfileRepository courierProfileRepository;
    private final ParcelBookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ParcelLifecycleRepository parcelLifecycleRepository;

    public CourierAssignmentService(
            ParcelAssignmentRepository assignmentRepository,
            CourierProfileRepository courierProfileRepository,
            ParcelBookingRepository bookingRepository,
            UserRepository userRepository,
            ParcelLifecycleRepository parcelLifecycleRepository) {
        this.assignmentRepository = assignmentRepository;
        this.courierProfileRepository = courierProfileRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.parcelLifecycleRepository = parcelLifecycleRepository;
    }

    @Transactional
    public ParcelAssignment assignCourier(UUID bookingId, UUID courierId, UUID dispatcherId) {
        // 1. Assignment Validation: Check for existing active assignments to prevent duplicates
        if (assignmentRepository.findByBookingIdAndIsActiveTrue(bookingId).isPresent()) {
            throw new IllegalStateException("This parcel already has an active courier assignment.");
        }

        // 2. Courier Profile Validation: Verify courier exists and is active
        CourierProfile courierProfile = courierProfileRepository.findById(courierId)
                .orElseThrow(() -> new IllegalArgumentException("Courier profile not found for the given courier ID."));
        if (!courierProfile.isActive()) {
            throw new IllegalStateException("The selected courier is currently marked inactive.");
        }

        // 3. Fetch required entities from other modules (M1 and M4)
        ParcelBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found."));
        
        User courier = userRepository.findById(courierId)
                .orElseThrow(() -> new IllegalArgumentException("Courier user not found."));
                
        User dispatcher = userRepository.findById(dispatcherId)
                .orElseThrow(() -> new IllegalArgumentException("Dispatcher user not found."));

        // 4. Courier Assignment: Create and save the relationship
        ParcelAssignment assignment = new ParcelAssignment();
        assignment.setBooking(booking);
        assignment.setCourier(courier);
        assignment.setAssignedBy(dispatcher);
        assignment.setActive(true);

        ParcelAssignment savedAssignment = assignmentRepository.save(assignment);

        // 5. Synchronize with M6 ParcelLifecycle: update assigned courier so delivery tasks appear for the courier
        parcelLifecycleRepository.findByBookingId(bookingId).ifPresent(lifecycle -> {
            lifecycle.assignCourier(courierId);
            parcelLifecycleRepository.save(lifecycle);
        });

        return savedAssignment;
    }

    // Courier Availability: Identify active couriers eligible to receive tasks
    public List<CourierProfile> getActiveCouriers() {
        return courierProfileRepository.findAllByIsActiveTrue();
    }

    // Courier Workload Calculation: Calculate a courier's current active deliveries
    public long getCourierWorkload(UUID courierId) {
        return assignmentRepository.countByCourierIdAndIsActiveTrue(courierId);
    }
    
    @Transactional
    public ParcelAssignment reassignCourier(UUID bookingId, UUID newCourierId, UUID dispatcherId, String reason) {
        // 1. Find the current assignment and revoke it
        ParcelAssignment currentAssignment = assignmentRepository.findByBookingIdAndIsActiveTrue(bookingId)
                .orElseThrow(() -> new IllegalStateException("No active assignment found to reassign."));
                
        if (currentAssignment.getCourier().getId().equals(newCourierId)) {
            throw new IllegalArgumentException("Parcel is already assigned to this courier.");
        }

        currentAssignment.setActive(false);
        currentAssignment.setRevokedAt(OffsetDateTime.now());
        currentAssignment.setRevocationReason(reason);
        assignmentRepository.save(currentAssignment);
        
        // 2. Create the new assignment using the standard logic
        return assignCourier(bookingId, newCourierId, dispatcherId);
    }
}
