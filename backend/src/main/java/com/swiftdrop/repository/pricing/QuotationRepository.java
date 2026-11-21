package com.swiftdrop.repository.pricing;

import com.swiftdrop.entity.pricing.Quotation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotationRepository extends JpaRepository<Quotation, UUID> {
}