package com.swiftdrop.controller.identity;

import com.swiftdrop.dto.identity.CourierRoleRequest;
import com.swiftdrop.dto.identity.RoleAssignmentResponse;
import com.swiftdrop.service.identity.IdentityRoleManagement;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminRoleController {
    private final IdentityRoleManagement roles;
    public AdminRoleController(IdentityRoleManagement roles) { this.roles = roles; }

    @GetMapping("/api/v1/admin/users/{id}/roles")
    public ResponseEntity<RoleAssignmentResponse> roles(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(roles.roleSummary(id));
    }

    @PostMapping("/api/v1/admin/users/{id}/courier-role")
    public ResponseEntity<RoleAssignmentResponse> grantCourier(@PathVariable UUID id,
            @Valid @RequestBody CourierRoleRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(roles.grantCourier(id, request.expectedVersion(), request.reason()));
    }
}
