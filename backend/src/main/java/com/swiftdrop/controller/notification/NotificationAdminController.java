package com.swiftdrop.controller.notification;

import com.swiftdrop.service.notification.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/notifications")
public class NotificationAdminController {
    private final NotificationService service;
    public NotificationAdminController(NotificationService service) { this.service=service; }
    public record TemplateUpdate(@NotBlank @Size(max=500) String body,boolean enabled,@PositiveOrZero long version) { }
    @GetMapping("/templates") public List<Map<String,Object>> templates() { return service.templates(); }
    @PutMapping("/templates/{status}") public void update(@PathVariable String status,@Valid @RequestBody TemplateUpdate request) { service.update(status,request.body(),request.enabled(),request.version()); }
    @GetMapping("/jobs") public List<Map<String,Object>> jobs() { return service.jobs(); }
    @GetMapping("/jobs/{id}/attempts") public List<Map<String,Object>> attempts(@PathVariable UUID id) { return service.attempts(id); }
    @PostMapping("/jobs/{id}/retry") public void retry(@PathVariable UUID id) { service.retry(id); }
}
