package com.swiftdrop.controller.identity;

import com.swiftdrop.dto.identity.*;
import com.swiftdrop.service.identity.IdentityRoleManagement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
public class AdminRoleController {
    private final IdentityRoleManagement roles;
    public AdminRoleController(IdentityRoleManagement roles) { this.roles=roles; }
    public record Change(@NotNull IdentityRole role,boolean grant,@PositiveOrZero long expectedVersion,@NotBlank @Size(max=500) String reason) { }
    @GetMapping("/api/v1/admin/users/{id}/roles") public org.springframework.http.ResponseEntity<RoleAssignmentResponse> roles(@PathVariable UUID id) {
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(roles.roleSummary(id));
    }
    @PostMapping("/api/v1/admin/users/{id}/courier-role") public org.springframework.http.ResponseEntity<RoleAssignmentResponse> courier(@PathVariable UUID id,@Valid @RequestBody CourierRoleRequest request) {
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(roles.grantCourier(id,request.expectedVersion(),request.reason()));
    }
    @PostMapping("/api/v1/admin/users/{id}/operational-roles") public RoleAssignmentResponse change(@PathVariable UUID id,@Valid @RequestBody Change request) {
        if(!request.grant()) return roles.revokeOperationalRole(id,request.role(),request.expectedVersion(),request.reason());
        return switch(request.role()) {
            case COURIER -> roles.grantCourier(id,request.expectedVersion(),request.reason());
            case DISPATCHER_ADMIN -> roles.grantAdministrator(id,request.expectedVersion(),request.reason());
            default -> throw new IllegalArgumentException("Unsupported role change");
        };
    }
}
