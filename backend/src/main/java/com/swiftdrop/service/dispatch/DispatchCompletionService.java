package com.swiftdrop.service.dispatch;

import com.swiftdrop.repository.dispatch.DispatchRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DispatchCompletionService {
    private final DispatchRepository dispatch;
    public DispatchCompletionService(DispatchRepository dispatch) { this.dispatch=dispatch; }
    @Transactional public void complete(UUID bookingId) { dispatch.revoke(bookingId,"Delivery completed"); }
}
