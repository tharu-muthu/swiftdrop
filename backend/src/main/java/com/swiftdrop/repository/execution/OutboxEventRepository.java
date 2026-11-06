package com.swiftdrop.repository.execution;

import com.swiftdrop.entity.execution.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> { }
