package com.swiftdrop.service.pricing;

import com.swiftdrop.dto.pricing.PostcodeRequest;
import com.swiftdrop.dto.pricing.RateCardRequest;
import com.swiftdrop.dto.pricing.RateCardResponse;
import com.swiftdrop.dto.pricing.ZoneRequest;
import com.swiftdrop.dto.pricing.ZoneResponse;
import com.swiftdrop.entity.pricing.RateCardVersion;
import com.swiftdrop.entity.pricing.Zone;
import com.swiftdrop.entity.pricing.ZonePostcode;
import com.swiftdrop.exception.PricingErrorCode;
import com.swiftdrop.exception.PricingException;
import com.swiftdrop.repository.pricing.RateCardRepository;
import com.swiftdrop.repository.pricing.ZonePostcodeRepository;
import com.swiftdrop.repository.pricing.ZoneRepository;
import com.swiftdrop.service.identity.IdentityAccountService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PRI-01 / PRI-02: maintain zones, postcode mappings and rate cards. DISPATCHER_ADMIN only. */
@Service
public class PricingAdminService {
    private static final String ADMIN_ROLE = "DISPATCHER_ADMIN";

    private final ZoneRepository zones;
    private final ZonePostcodeRepository postcodes;
    private final RateCardRepository rateCards;
    private final IdentityAccountService identities;
    private final Clock clock;

    public PricingAdminService(ZoneRepository zones, ZonePostcodeRepository postcodes, RateCardRepository rateCards,
            IdentityAccountService identities, Clock clock) {
        this.zones = zones;
        this.postcodes = postcodes;
        this.rateCards = rateCards;
        this.identities = identities;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ZoneResponse> listZones() {
        requireAdmin();
        return zones.findAll().stream().map(PricingAdminService::toResponse).toList();
    }

    @Transactional
    public ZoneResponse createZone(ZoneRequest request) {
        requireAdmin();
        String name = request.name().trim();
        if (zones.existsByName(name)) {
            throw new PricingException(PricingErrorCode.ZONE_NAME_TAKEN);
        }
        return toResponse(zones.save(new Zone(name)));
    }

    @Transactional
    public void addPostcode(UUID zoneId, PostcodeRequest request) {
        requireAdmin();
        requireZone(zoneId);
        String code = request.postcode().trim();
        if (postcodes.existsByPostcode(code)) {
            throw new PricingException(PricingErrorCode.POSTCODE_ALREADY_MAPPED);
        }
        postcodes.save(new ZonePostcode(code, zoneId));
    }

    @Transactional(readOnly = true)
    public List<RateCardResponse> listRateCards() {
        requireAdmin();
        return rateCards.findAll().stream().map(PricingAdminService::toResponse).toList();
    }

    @Transactional
    public RateCardResponse createRateCard(RateCardRequest request) {
        requireAdmin();
        requireZone(request.pickupZoneId());
        requireZone(request.dropoffZoneId());
        var effectiveFrom = request.effectiveFrom() != null ? request.effectiveFrom() : clock.instant();
        if (rateCards.existsByPickupZoneIdAndDropoffZoneIdAndEffectiveFrom(
                request.pickupZoneId(), request.dropoffZoneId(), effectiveFrom)) {
            throw new PricingException(PricingErrorCode.RATE_CARD_EXISTS);
        }
        return toResponse(rateCards.save(new RateCardVersion(request.pickupZoneId(), request.dropoffZoneId(),
                request.baseMinor(), request.includedKg(), request.extraKgMinor(), request.sizeSurchargeS(),
                request.sizeSurchargeM(), request.sizeSurchargeL(), effectiveFrom)));
    }

    private void requireAdmin() {
        if (!identities.requireCurrentIdentity().roles().contains(ADMIN_ROLE)) {
            throw new AccessDeniedException("Administrator role required");
        }
    }

    private void requireZone(UUID zoneId) {
        if (!zones.existsById(zoneId)) {
            throw new PricingException(PricingErrorCode.ZONE_NOT_FOUND);
        }
    }

    private static ZoneResponse toResponse(Zone zone) {
        return new ZoneResponse(zone.getId(), zone.getName(), zone.isActive());
    }

    private static RateCardResponse toResponse(RateCardVersion r) {
        return new RateCardResponse(r.getId(), r.getPickupZoneId(), r.getDropoffZoneId(), r.getCurrency(),
                r.getBaseMinor(), r.getIncludedKg(), r.getExtraKgMinor(), r.getSizeSurchargeS(),
                r.getSizeSurchargeM(), r.getSizeSurchargeL(), r.getEffectiveFrom(), r.isActive());
    }
}