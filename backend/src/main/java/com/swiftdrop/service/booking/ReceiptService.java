package com.swiftdrop.service.booking;

import com.swiftdrop.dto.booking.BookingSnapshot;
import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.exception.LifecycleConflictException;
import com.swiftdrop.service.identity.IdentityAccountService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class ReceiptService {
    private final BookingReadService bookings; private final IdentityAccountService identities;
    public ReceiptService(BookingReadService bookings,IdentityAccountService identities) { this.bookings=bookings; this.identities=identities; }
    @Transactional(readOnly=true) public BookingSnapshot receipt(UUID id) {
        var customer=identities.requireCurrentRole(IdentityRole.CUSTOMER);
        var booking=bookings.find(id).filter(row->customer.userId().equals(row.customerId())).orElseThrow(()->new AccessDeniedException("Access denied"));
        if(!"ISSUED".equals(booking.receiptState())) throw new LifecycleConflictException("The delivery receipt becomes available after delivery.");
        return booking;
    }
}
