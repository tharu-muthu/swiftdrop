package com.swiftdrop.service.execution;

import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.integration.execution.DeadLetterAdapter;
import com.swiftdrop.service.identity.IdentityAccountService;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class IntegrationOperationsService {
    private final IdentityAccountService identities; private final DeadLetterAdapter broker;
    public IntegrationOperationsService(IdentityAccountService identities,DeadLetterAdapter broker) { this.identities=identities;this.broker=broker; }
    public List<Map<String,Object>> queues() { identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN);return broker.queues(); }
    public int replay(String consumer,int limit) { identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN);return broker.replay(consumer,limit); }
}
