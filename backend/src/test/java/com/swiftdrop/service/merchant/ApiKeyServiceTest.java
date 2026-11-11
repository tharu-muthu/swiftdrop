
package com.swiftdrop.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.entity.merchant.ApiCredential;
import com.swiftdrop.entity.merchant.Merchant;
import com.swiftdrop.exception.ApiKeyLimitException;
import com.swiftdrop.exception.ApiKeyNotFoundException;
import com.swiftdrop.exception.MerchantNotApprovedException;
import com.swiftdrop.exception.MerchantNotFoundException;
import com.swiftdrop.repository.merchant.ApiCredentialRepository;
import com.swiftdrop.repository.merchant.MerchantRepository;
import com.swiftdrop.security.IdentityContext;
import com.swiftdrop.service.identity.IdentityAccountService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ApiKeyServiceTest {
    final IdentityAccountService identities = mock(IdentityAccountService.class);
    final MerchantRepository merchants = mock(MerchantRepository.class);
    final ApiCredentialRepository credentials = mock(ApiCredentialRepository.class);
    final ApiKeyService service = new ApiKeyService(identities, merchants, credentials, new ApiKeyGenerator());

    final UUID ownerId = UUID.randomUUID();
    final IdentityContext owner = new IdentityContext(ownerId, UUID.randomUUID(), Set.of("CUSTOMER"), 1);

    Merchant merchant(boolean approved) {
        var merchant = Merchant.register(ownerId, "Shop", "shop@example.com", null, null);
        if (approved) {
            merchant.approve(UUID.randomUUID(), Instant.now());
        }
        return merchant;
    }

    void signedInWith(Merchant merchant) {
        when(identities.requireCurrentIdentity()).thenReturn(owner);
        when(merchants.findByOwnerUserId(ownerId)).thenReturn(Optional.of(merchant));
        when(merchants.findForUpdate(any())).thenReturn(Optional.of(merchant));
    }

    @Test
    void approvedOwnerGetsAKeyAndOnlyItsHashIsStored() {
        signedInWith(merchant(true));

        var created = service.create();

        var saved = ArgumentCaptor.forClass(ApiCredential.class);
        verify(credentials).saveAndFlush(saved.capture());
        var key = created.apiKey();
        assertThat(key).startsWith("sdk_").contains(".");
        assertThat(saved.getValue().getKeyPrefix()).isEqualTo(key.substring(0, key.indexOf('.')));
        assertThat(saved.getValue().getSecretHash()).isEqualTo(ApiKeyGenerator.sha256Hex(key))
                .matches("^[0-9a-f]{64}$").isNotEqualTo(key);
        assertThat(created.credential().active()).isTrue();
    }

    @Test
    void theResponseSummaryNeverRepeatsTheSecret() {
        signedInWith(merchant(true));

        var created = service.create();

        assertThat(created.credential().toString()).doesNotContain(created.apiKey());
    }

    @Test
    void pendingMerchantCannotCreateAKey() {
        signedInWith(merchant(false));

        assertThatThrownBy(service::create).isInstanceOf(MerchantNotApprovedException.class);
        verify(credentials, never()).saveAndFlush(any());
    }

    @Test
    void userWithoutABusinessIsNotFound() {
        when(identities.requireCurrentIdentity()).thenReturn(owner);
        when(merchants.findByOwnerUserId(ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(service::create).isInstanceOf(MerchantNotFoundException.class);
    }

    @Test
    void fifthActiveKeyIsAllowedButTheSixthIsNot() {
        var approved = merchant(true);
        signedInWith(approved);

        when(credentials.countByMerchantIdAndRevokedAtIsNull(approved.getId())).thenReturn(4L);
        service.create();

        when(credentials.countByMerchantIdAndRevokedAtIsNull(approved.getId())).thenReturn(5L);
        assertThatThrownBy(service::create).isInstanceOf(ApiKeyLimitException.class);
    }

    @Test
    void listShowsOnlyThisMerchantsKeys() {
        var approved = merchant(true);
        signedInWith(approved);
        var credential = ApiCredential.issue(approved.getId(), "sdk_abc", "a".repeat(64), "quote,order");
        when(credentials.findByMerchantIdOrderByCreatedAtDesc(approved.getId())).thenReturn(List.of(credential));

        var keys = service.list();

        assertThat(keys).hasSize(1);
        assertThat(keys.get(0).keyPrefix()).isEqualTo("sdk_abc");
        verify(credentials).findByMerchantIdOrderByCreatedAtDesc(approved.getId());
    }

    @Test
    void revokingMarksTheKeyRevoked() {
        var approved = merchant(true);
        signedInWith(approved);
        var credentialId = UUID.randomUUID();
        var credential = ApiCredential.issue(approved.getId(), "sdk_abc", "a".repeat(64), "quote,order");
        when(credentials.findByIdAndMerchantId(credentialId, approved.getId())).thenReturn(Optional.of(credential));

        var summary = service.revoke(credentialId);

        assertThat(credential.isRevoked()).isTrue();
        assertThat(summary.active()).isFalse();
    }

    @Test
    void revokingTwiceKeepsTheOriginalRevocationTime() {
        var approved = merchant(true);
        signedInWith(approved);
        var credentialId = UUID.randomUUID();
        var credential = ApiCredential.issue(approved.getId(), "sdk_abc", "a".repeat(64), "quote,order");
        credential.revoke(Instant.parse("2026-01-01T00:00:00Z"));
        when(credentials.findByIdAndMerchantId(credentialId, approved.getId())).thenReturn(Optional.of(credential));

        service.revoke(credentialId);

        assertThat(credential.getRevokedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void anotherMerchantsKeyLooksLikeItDoesNotExist() {
        var approved = merchant(true);
        signedInWith(approved);
        var foreignKeyId = UUID.randomUUID();
        when(credentials.findByIdAndMerchantId(foreignKeyId, approved.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(foreignKeyId)).isInstanceOf(ApiKeyNotFoundException.class);
    }
}