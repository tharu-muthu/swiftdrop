package com.swiftdrop.service.identity;

import com.swiftdrop.dto.identity.AdminUserResponse;
import com.swiftdrop.dto.identity.AdminUsersPage;
import java.util.UUID;

public interface AdminUserService {
    AdminUsersPage list(String query, int page, int size);
    AdminUserResponse get(UUID id);
    AdminUserResponse suspend(UUID id, long expectedVersion, String reason);
    AdminUserResponse reactivate(UUID id, long expectedVersion, String reason);
}
