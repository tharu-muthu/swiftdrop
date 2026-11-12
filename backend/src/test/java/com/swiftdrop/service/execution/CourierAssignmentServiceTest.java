package com.swiftdrop.service.execution;

import com.swiftdrop.entity.booking.ParcelBooking;
import com.swiftdrop.entity.execution.CourierProfile;
import com.swiftdrop.entity.execution.ParcelAssignment;
import com.swiftdrop.entity.execution.ParcelLifecycle;
import com.swiftdrop.entity.identity.User;
import com.swiftdrop.repository.booking.ParcelBookingRepository;
import com.swiftdrop.repository.execution.CourierProfileRepository;
import com.swiftdrop.repository.execution.ParcelAssignmentRepository;
import com.swiftdrop.repository.execution.ParcelLifecycleRepository;
import com.swiftdrop.repository.identity.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CourierAssignmentServiceTest {

    private final ParcelAssignmentRepository assignmentRepository = mock(ParcelAssignmentRepository.class);
    private final CourierProfileRepository courierProfileRepository = mock(CourierProfileRepository.class);
    private final ParcelBookingRepository bookingRepository = mock(ParcelBookingRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ParcelLifecycleRepository parcelLifecycleRepository = mock(ParcelLifecycleRepository.class);

    private CourierAssignmentService service;

    private final UUID bookingId = UUID.randomUUID();
    private final UUID courierId = UUID.randomUUID();
    private final UUID newCourierId = UUID.randomUUID();
    private final UUID dispatcherId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CourierAssignmentService(
                assignmentRepository,
                courierProfileRepository,
                bookingRepository,
                userRepository,
                parcelLifecycleRepository
        );
    }

    @Test
    void assignsCourierSuccessfullyAndSyncsWithLifecycle() {
        when(assignmentRepository.findByBookingIdAndIsActiveTrue(bookingId)).thenReturn(Optional.empty());

        CourierProfile profile = new CourierProfile();
        profile.setUserId(courierId);
        profile.setActive(true);
        when(courierProfileRepository.findById(courierId)).thenReturn(Optional.of(profile));

        ParcelBooking booking = mock(ParcelBooking.class);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        User courier = mock(User.class);
        when(userRepository.findById(courierId)).thenReturn(Optional.of(courier));

        User dispatcher = mock(User.class);
        when(userRepository.findById(dispatcherId)).thenReturn(Optional.of(dispatcher));

        when(assignmentRepository.save(any(ParcelAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ParcelLifecycle lifecycle = mock(ParcelLifecycle.class);
        when(parcelLifecycleRepository.findByBookingId(bookingId)).thenReturn(Optional.of(lifecycle));

        ParcelAssignment result = service.assignCourier(bookingId, courierId, dispatcherId);

        assertThat(result).isNotNull();
        assertThat(result.isActive()).isTrue();
        assertThat(result.getCourier()).isEqualTo(courier);
        assertThat(result.getAssignedBy()).isEqualTo(dispatcher);
        assertThat(result.getBooking()).isEqualTo(booking);

        verify(assignmentRepository).save(any(ParcelAssignment.class));
        verify(lifecycle).assignCourier(courierId);
        verify(parcelLifecycleRepository).save(lifecycle);
    }

    @Test
    void rejectsDuplicateAssignmentWhenParcelAlreadyHasActiveCourier() {
        ParcelAssignment existing = mock(ParcelAssignment.class);
        when(assignmentRepository.findByBookingIdAndIsActiveTrue(bookingId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.assignCourier(bookingId, courierId, dispatcherId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already has an active courier assignment");

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void rejectsAssignmentWhenCourierProfileIsInactive() {
        when(assignmentRepository.findByBookingIdAndIsActiveTrue(bookingId)).thenReturn(Optional.empty());

        CourierProfile profile = new CourierProfile();
        profile.setUserId(courierId);
        profile.setActive(false);
        when(courierProfileRepository.findById(courierId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.assignCourier(bookingId, courierId, dispatcherId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currently marked inactive");

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void reassignsCourierSuccessfully() {
        ParcelAssignment currentAssignment = new ParcelAssignment();
        User oldCourier = mock(User.class);
        when(oldCourier.getId()).thenReturn(courierId);
        currentAssignment.setCourier(oldCourier);
        currentAssignment.setActive(true);

        when(assignmentRepository.findByBookingIdAndIsActiveTrue(bookingId))
                .thenReturn(Optional.of(currentAssignment))
                .thenReturn(Optional.empty());

        CourierProfile newProfile = new CourierProfile();
        newProfile.setUserId(newCourierId);
        newProfile.setActive(true);
        when(courierProfileRepository.findById(newCourierId)).thenReturn(Optional.of(newProfile));

        ParcelBooking booking = mock(ParcelBooking.class);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        User newCourier = mock(User.class);
        when(newCourier.getId()).thenReturn(newCourierId);
        when(userRepository.findById(newCourierId)).thenReturn(Optional.of(newCourier));

        User dispatcher = mock(User.class);
        when(userRepository.findById(dispatcherId)).thenReturn(Optional.of(dispatcher));

        when(assignmentRepository.save(any(ParcelAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ParcelAssignment result = service.reassignCourier(bookingId, newCourierId, dispatcherId, "Vehicle issue");

        assertThat(currentAssignment.isActive()).isFalse();
        assertThat(currentAssignment.getRevokedAt()).isNotNull();
        assertThat(currentAssignment.getRevocationReason()).isEqualTo("Vehicle issue");

        assertThat(result.getCourier()).isEqualTo(newCourier);
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void rejectsReassignmentToSameCourier() {
        ParcelAssignment currentAssignment = new ParcelAssignment();
        User courier = mock(User.class);
        when(courier.getId()).thenReturn(courierId);
        currentAssignment.setCourier(courier);
        currentAssignment.setActive(true);

        when(assignmentRepository.findByBookingIdAndIsActiveTrue(bookingId)).thenReturn(Optional.of(currentAssignment));

        assertThatThrownBy(() -> service.reassignCourier(bookingId, courierId, dispatcherId, "Same courier"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already assigned to this courier");
    }

    @Test
    void calculatesCourierWorkloadAccurately() {
        when(assignmentRepository.countByCourierIdAndIsActiveTrue(courierId)).thenReturn(3L);

        long workload = service.getCourierWorkload(courierId);

        assertThat(workload).isEqualTo(3L);
        verify(assignmentRepository).countByCourierIdAndIsActiveTrue(courierId);
    }

    @Test
    void returnsActiveCouriers() {
        CourierProfile p1 = new CourierProfile();
        p1.setUserId(courierId);
        p1.setActive(true);

        when(courierProfileRepository.findAllByIsActiveTrue()).thenReturn(List.of(p1));

        List<CourierProfile> activeCouriers = service.getActiveCouriers();

        assertThat(activeCouriers).hasSize(1);
        assertThat(activeCouriers.get(0).getUserId()).isEqualTo(courierId);
    }
}
