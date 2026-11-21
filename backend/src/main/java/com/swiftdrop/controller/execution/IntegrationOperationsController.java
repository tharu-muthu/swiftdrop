package com.swiftdrop.controller.execution;

import com.swiftdrop.service.execution.IntegrationOperationsService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/integrations")
public class IntegrationOperationsController {
    private final IntegrationOperationsService service;
    public IntegrationOperationsController(IntegrationOperationsService service) { this.service=service; }
    @GetMapping("/queues") public List<Map<String,Object>> queues() { return service.queues(); }
    @PostMapping("/{consumer}/replay") public Map<String,Integer> replay(@PathVariable String consumer,@RequestParam(defaultValue="20") int limit) { return Map.of("replayed",service.replay(consumer,limit)); }
}
