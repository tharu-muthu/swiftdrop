package com.swiftdrop.service.execution;

import com.swiftdrop.repository.execution.IntegrationReceiptRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Claim and consumer effects must commit together, before the broker acknowledgement. */
@Service
public class IntegrationReceiptService {
    private final IntegrationReceiptRepository receipts;
    public IntegrationReceiptService(IntegrationReceiptRepository receipts) { this.receipts=receipts; }
    @Transactional(propagation=Propagation.MANDATORY)
    public boolean claim(String consumer,UUID eventId) { return receipts.claim(consumer,eventId); }
}
