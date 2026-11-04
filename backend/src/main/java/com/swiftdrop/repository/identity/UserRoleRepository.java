package com.swiftdrop.repository.identity;

import com.swiftdrop.entity.identity.UserRole;
import com.swiftdrop.entity.identity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> { }
