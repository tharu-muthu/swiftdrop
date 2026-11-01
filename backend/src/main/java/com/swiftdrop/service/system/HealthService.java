package com.swiftdrop.service.system;

import com.swiftdrop.dto.system.HealthResponse;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
    public HealthResponse getHealth() {
        // Liveness only. Do not imply that broker delivery or business features work.
        return new HealthResponse("UP", "swiftdrop");
    }
}
