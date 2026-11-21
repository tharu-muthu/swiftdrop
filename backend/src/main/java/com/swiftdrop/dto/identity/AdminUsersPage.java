package com.swiftdrop.dto.identity;

import java.util.List;

public record AdminUsersPage(List<AdminUserResponse> items, int page, int size,
        long totalElements, int totalPages) { }
