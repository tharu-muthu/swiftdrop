package com.swiftdrop.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.dto.merchant.MerchantRegistrationRequest;
import com.swiftdrop.entity.merchant.Merchant;
import com.swiftdrop.entity.merchant.MerchantApprovalStatus;
import com.swiftdrop.exception.MerchantAlreadyRegisteredException;
import com.swiftdrop.repository.merchant.MerchantRepository;
import com.swiftdrop.security.IdentityContext;
import com.swiftdrop.service.identity.IdentityAccountService;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

class MerchantServiceTest {
    final IdentityAccountService identities = mock(IdentityAccountService.class);
    final MerchantRepository merchants = mock(MerchantRepository.class);
    final MerchantService service = new MerchantService(identities, merchants);

    final UUID owner = UUID.randomUUID();
    final IdentityContext customer = new IdentityContext(owner, UUID.randomUUID(), Set.of("CUSTOMER"), 1);
    final MerchantRegistrationRequest request = new MerchantRegistrationRequest(
            "  Lanka Crafts  ", " shop@example.com ", "+94 77 123 4567", "12 Galle Road, Colombo");

    @Test
    void registrationCreatesPendingMerchantOwnedByTheSignedInUser() {
        when(identities.requireCurrentRole(IdentityRole.CUSTOMER)).thenReturn(customer);
        when(merchants.existsByOwnerUserId(owner)).thenReturn(false);

        var response = service.register(request);

        var saved = ArgumentCaptor.forClass(Merchant.class);
        verify(merchants).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getOwnerUserId()).isEqualTo(owner);
        assertThat(saved.getValue().getApprovalStatus()).isEqualTo(MerchantApprovalStatus.PENDING);
        assertThat(saved.getValue().getBusinessName()).isEqualTo("Lanka Crafts");
        assertThat(saved.getValue().getContactEmail()).isEqualTo("shop@example.com");
        assertThat(response.approvalStatus()).isEqualTo("PENDING");
    }

    @Test
    void blankOptionalFieldsAreStoredAsNull() {
        when(identities.requireCurrentRole(IdentityRole.CUSTOMER)).thenReturn(customer);
        var sparse = new MerchantRegistrationRequest("Shop", "shop@example.com", "  ", "");

        service.register(sparse);

        var saved = ArgumentCaptor.forClass(Merchant.class);
        verify(merchants).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getContactPhone()).isNull();
        assertThat(saved.getValue().getBusinessAddress()).isNull();
    }

    @Test
    void secondRegistrationForTheSameOwnerIsRejectedWithoutSaving() {
        when(identities.requireCurrentRole(IdentityRole.CUSTOMER)).thenReturn(customer);
        when(merchants.existsByOwnerUserId(owner)).thenReturn(true);

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(MerchantAlreadyRegisteredException.class);
        verify(merchants, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicateCaughtByTheDatabaseBecomesTheSameConflict() {
        when(identities.requireCurrentRole(IdentityRole.CUSTOMER)).thenReturn(customer);
        when(merchants.existsByOwnerUserId(owner)).thenReturn(false);
        when(merchants.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_merchants_owner"));

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(MerchantAlreadyRegisteredException.class);
    }

    @Test
    void userWithoutTheCustomerRoleCannotRegisterAndNothingIsTouched() {
        when(identities.requireCurrentRole(IdentityRole.CUSTOMER)).thenThrow(new AccessDeniedException("Access denied"));

        assertThatThrownBy(() -> service.register(request)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(merchants);
    }

    @Test
    void myApplicationReturnsOnlyTheCurrentUsersOwnRecord() {
        when(identities.requireCurrentIdentity()).thenReturn(customer);
        var existing = Merchant.register(owner, "Shop", "shop@example.com", null, null);
        when(merchants.findByOwnerUserId(owner)).thenReturn(Optional.of(existing));

        var found = service.myApplication();

        assertThat(found).isPresent();
        assertThat(found.get().businessName()).isEqualTo("Shop");
        verify(merchants).findByOwnerUserId(owner);
    }

    @Test
    void myApplicationIsEmptyWhenTheUserNeverApplied() {
        when(identities.requireCurrentIdentity()).thenReturn(customer);
        when(merchants.findByOwnerUserId(owner)).thenReturn(Optional.empty());

        assertThat(service.myApplication()).isEmpty();
    }
}