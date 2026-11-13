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
import com.swiftdrop.entity.merchant.Merchant;
import com.swiftdrop.entity.merchant.MerchantApprovalStatus;
import com.swiftdrop.exception.MerchantDecisionConflictException;
import com.swiftdrop.exception.MerchantNotFoundException;
import com.swiftdrop.repository.merchant.MerchantRepository;
import com.swiftdrop.security.IdentityContext;
import com.swiftdrop.service.identity.IdentityAccountService;
import com.swiftdrop.service.identity.IdentityRoleManagement;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;

class MerchantReviewServiceTest {
    final IdentityAccountService identities = mock(IdentityAccountService.class);
    final MerchantRepository merchants = mock(MerchantRepository.class);
    final IdentityRoleManagement roleManagement = mock(IdentityRoleManagement.class);
    final MerchantReviewService service = new MerchantReviewService(identities, merchants, roleManagement);

    final UUID adminId = UUID.randomUUID();
    final UUID merchantId = UUID.randomUUID();
    final IdentityContext admin = new IdentityContext(adminId, UUID.randomUUID(), Set.of("DISPATCHER_ADMIN"), 1);

    Merchant pending() {
        return Merchant.register(UUID.randomUUID(), "Shop", "shop@example.com", null, null);
    }

    @Test
    void approvingAPendingApplicationRecordsTheReviewer() {
        when(identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN)).thenReturn(admin);
        var merchant = pending();
        when(merchants.findForUpdate(merchantId)).thenReturn(Optional.of(merchant));

        var response = service.approve(merchantId);

        assertThat(response.approvalStatus()).isEqualTo("APPROVED");
        assertThat(merchant.getReviewedBy()).isEqualTo(adminId);
        assertThat(merchant.getReviewedAt()).isNotNull();
        verify(merchants).saveAndFlush(merchant);
        verify(roleManagement).grantMerchantForApprovedOwner(merchant.getOwnerUserId(), "Merchant application approved");
    }

    @Test
    void rejectingStoresTheTrimmedReason() {
        when(identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN)).thenReturn(admin);
        var merchant = pending();
        when(merchants.findForUpdate(merchantId)).thenReturn(Optional.of(merchant));

        var response = service.reject(merchantId, "  Incomplete documents  ");

        assertThat(response.approvalStatus()).isEqualTo("REJECTED");
        assertThat(response.reviewReason()).isEqualTo("Incomplete documents");
        assertThat(merchant.getReviewedBy()).isEqualTo(adminId);
    }

    @Test
    void anAlreadyDecidedApplicationCannotBeDecidedAgain() {
        when(identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN)).thenReturn(admin);
        var merchant = pending();
        merchant.approve(adminId, java.time.Instant.now());
        when(merchants.findForUpdate(merchantId)).thenReturn(Optional.of(merchant));

        assertThatThrownBy(() -> service.reject(merchantId, "Changed my mind"))
                .isInstanceOf(MerchantDecisionConflictException.class);
        assertThat(merchant.getApprovalStatus()).isEqualTo(MerchantApprovalStatus.APPROVED);
        verify(merchants, never()).saveAndFlush(any());
    }

    @Test
    void unknownApplicationIsNotFound() {
        when(identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN)).thenReturn(admin);
        when(merchants.findForUpdate(merchantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(merchantId)).isInstanceOf(MerchantNotFoundException.class);
    }

    @Test
    void nonAdminCannotDecideAndNothingIsLoaded() {
        when(identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN))
                .thenThrow(new AccessDeniedException("Access denied"));

        assertThatThrownBy(() -> service.approve(merchantId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.reject(merchantId, "no")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(merchants);
    }

    @Test
    void nonAdminCannotListApplications() {
        when(identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN))
                .thenThrow(new AccessDeniedException("Access denied"));

        assertThatThrownBy(() -> service.list(MerchantApprovalStatus.PENDING, 0, 50))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(merchants);
    }

    @Test
    void listingCapsThePageSizeAtOneHundred() {
        when(identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN)).thenReturn(admin);
        when(merchants.findByApprovalStatus(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.list(MerchantApprovalStatus.PENDING, -5, 5000);

        var paging = ArgumentCaptor.forClass(Pageable.class);
        verify(merchants).findByApprovalStatus(any(), paging.capture());
        assertThat(paging.getValue().getPageSize()).isEqualTo(100);
        assertThat(paging.getValue().getPageNumber()).isZero();
    }
}
