package com.swiftdrop.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swiftdrop.entity.merchant.ApiCredential;
import com.swiftdrop.entity.merchant.Merchant;
import com.swiftdrop.exception.InvalidApiKeyException;
import com.swiftdrop.exception.MerchantNotApprovedException;
import com.swiftdrop.exception.MerchantNotFoundException;
import com.swiftdrop.repository.merchant.ApiCredentialRepository;
import com.swiftdrop.repository.merchant.MerchantRepository;
import com.swiftdrop.security.IdentityContext;
import com.swiftdrop.security.MerchantApiPrincipal;
import com.swiftdrop.service.identity.IdentityAccountService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class MerchantAccessServiceTest {
    final IdentityAccountService identities = mock(IdentityAccountService.class);
    final MerchantRepository merchants = mock(MerchantRepository.class);
    final ApiCredentialRepository credentials = mock(ApiCredentialRepository.class);
    final MerchantAccessService service = new MerchantAccessService(identities, merchants, credentials);

    final UUID merchantId = UUID.randomUUID();
    final UUID credentialId = UUID.randomUUID();
    final UUID ownerId = UUID.randomUUID();

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    Merchant merchant(boolean approved) {
        var merchant = Merchant.register(ownerId, "Lanka Crafts", "shop@example.com", null, null);
        if (approved) {
            merchant.approve(UUID.randomUUID(), Instant.now());
        }
        return merchant;
    }

    ApiCredential credential() {
        return ApiCredential.issue(merchantId, "sdk_abcdef0123", "a".repeat(64), "quote,order");
    }

    void signInWithKey() {
        var principal = new MerchantApiPrincipal(merchantId, credentialId, Set.of("quote", "order"));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    @Test
    void apiMerchantIsResolvedFromTheKeyPrincipal() {
        signInWithKey();
        when(credentials.findById(credentialId)).thenReturn(Optional.of(credential()));
        when(merchants.findById(merchantId)).thenReturn(Optional.of(merchant(true)));

        var context = service.requireCurrentApiMerchant();

        assertThat(context.merchantId()).isEqualTo(merchantId);
        assertThat(context.businessName()).isEqualTo("Lanka Crafts");
        assertThat(context.credentialId()).isEqualTo(credentialId);
        assertThat(context.scopes()).isEqualTo(Set.of("quote", "order"));
    }

    @Test
    void aKeyRevokedDuringTheSessionStopsWorking() {
        signInWithKey();
        var revoked = credential();
        revoked.revoke(Instant.now());
        when(credentials.findById(credentialId)).thenReturn(Optional.of(revoked));

        assertThatThrownBy(service::requireCurrentApiMerchant).isInstanceOf(InvalidApiKeyException.class);
    }

    @Test
    void aBusinessThatIsNoLongerApprovedIsDenied() {
        signInWithKey();
        when(credentials.findById(credentialId)).thenReturn(Optional.of(credential()));
        when(merchants.findById(merchantId)).thenReturn(Optional.of(merchant(false)));

        assertThatThrownBy(service::requireCurrentApiMerchant).isInstanceOf(MerchantNotApprovedException.class);
    }

    @Test
    void noPrincipalOrAUserIdentityIsNotAnApiMerchant() {
        assertThatThrownBy(service::requireCurrentApiMerchant).isInstanceOf(InvalidApiKeyException.class);

        var user = new IdentityContext(ownerId, UUID.randomUUID(), Set.of("CUSTOMER"), 1);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
        assertThatThrownBy(service::requireCurrentApiMerchant).isInstanceOf(InvalidApiKeyException.class);
    }

    @Test
    void approvedOwnerResolvesTheirOwnBusiness() {
        when(identities.requireCurrentIdentity())
                .thenReturn(new IdentityContext(ownerId, UUID.randomUUID(), Set.of("CUSTOMER"), 1));
        when(merchants.findByOwnerUserId(ownerId)).thenReturn(Optional.of(merchant(true)));

        var context = service.requireApprovedOwnerMerchant();

        assertThat(context.ownerUserId()).isEqualTo(ownerId);
        assertThat(context.businessName()).isEqualTo("Lanka Crafts");
        assertThat(context.credentialId()).isNull();
    }

    @Test
    void pendingOwnerIsDeniedAndOwnerWithoutABusinessIsNotFound() {
        when(identities.requireCurrentIdentity())
                .thenReturn(new IdentityContext(ownerId, UUID.randomUUID(), Set.of("CUSTOMER"), 1));
        when(merchants.findByOwnerUserId(ownerId)).thenReturn(Optional.of(merchant(false)));
        assertThatThrownBy(service::requireApprovedOwnerMerchant).isInstanceOf(MerchantNotApprovedException.class);

        when(merchants.findByOwnerUserId(ownerId)).thenReturn(Optional.empty());
        assertThatThrownBy(service::requireApprovedOwnerMerchant).isInstanceOf(MerchantNotFoundException.class);
    }
}