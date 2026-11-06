package com.swiftdrop.service.execution;

import com.swiftdrop.dto.execution.StatusUpdateRequest;
import com.swiftdrop.entity.execution.ParcelStatus;
import com.swiftdrop.exception.LifecycleConflictException;

final class ParcelStatusTransitionValidator {
    private ParcelStatusTransitionValidator() { }

    static void validate(ParcelStatus expected, ParcelStatus next, StatusUpdateRequest request) {
        boolean valid = switch (expected) {
            case CREATED -> next == ParcelStatus.PICKED_UP;
            case PICKED_UP -> next == ParcelStatus.IN_TRANSIT;
            case IN_TRANSIT -> next == ParcelStatus.OUT_FOR_DELIVERY;
            case OUT_FOR_DELIVERY -> next == ParcelStatus.DELIVERED || next == ParcelStatus.FAILED;
            case DELIVERED, FAILED -> false;
        };
        if (!valid) throw new LifecycleConflictException("Invalid parcel status transition.");
        if (next == ParcelStatus.DELIVERED && (request.recipientName() == null || request.recipientName().isBlank())) {
            throw new LifecycleConflictException("Recipient name is required for delivery confirmation.");
        }
        if (next == ParcelStatus.FAILED && (request.failureReason() == null || request.failureReason().isBlank())) {
            throw new LifecycleConflictException("A failure reason is required.");
        }
        if (next != ParcelStatus.DELIVERED && request.recipientName() != null
                || next != ParcelStatus.FAILED && request.failureReason() != null) {
            throw new LifecycleConflictException("Proof or failure details do not match the requested status.");
        }
    }
}
