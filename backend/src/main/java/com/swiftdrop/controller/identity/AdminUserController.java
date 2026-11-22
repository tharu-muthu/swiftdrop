package com.swiftdrop.controller.identity;

import com.swiftdrop.dto.identity.AccountStatusRequest;
import com.swiftdrop.dto.identity.AdminUserResponse;
import com.swiftdrop.dto.identity.AdminUsersPage;
import com.swiftdrop.service.identity.AdminUserService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminUserController {
    private final AdminUserService users;
    public AdminUserController(AdminUserService users) { this.users = users; }

    @GetMapping("/api/v1/admin/users")
    public ResponseEntity<AdminUsersPage> list(@RequestParam(required = false) String query,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(users.list(query, role, page, size));
    }

    @GetMapping("/api/v1/admin/users/{id}")
    public ResponseEntity<AdminUserResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(users.get(id));
    }

    @PostMapping("/api/v1/admin/users/{id}/suspend")
    public ResponseEntity<AdminUserResponse> suspend(@PathVariable UUID id,
            @Valid @RequestBody AccountStatusRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(users.suspend(id, request.expectedVersion(), request.reason()));
    }

    @PostMapping("/api/v1/admin/users/{id}/reactivate")
    public ResponseEntity<AdminUserResponse> reactivate(@PathVariable UUID id,
            @Valid @RequestBody AccountStatusRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(users.reactivate(id, request.expectedVersion(), request.reason()));
    }
}
