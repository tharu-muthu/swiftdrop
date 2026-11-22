package com.swiftdrop.service.dispatch;

import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.repository.dispatch.DispatchRepository;
import com.swiftdrop.service.identity.IdentityAccountService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** M5 read contract for the current courier's dated assignments. */
@Service
public class CourierAssignmentQuery {
    private final DispatchRepository dispatch;
    private final IdentityAccountService identities;
    public CourierAssignmentQuery(DispatchRepository dispatch, IdentityAccountService identities) {
        this.dispatch = dispatch;
        this.identities = identities;
    }
    @Transactional(readOnly = true)
    public List<UUID> bookingsAssignedBetween(Instant start, Instant end) {
        var courier = identities.requireCurrentRole(IdentityRole.COURIER);
        return dispatch.bookingsAssignedBetween(courier.userId(), start, end);
    }
}
