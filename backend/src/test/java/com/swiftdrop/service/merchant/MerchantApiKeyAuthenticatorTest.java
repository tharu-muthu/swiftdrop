package com.swiftdrop.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.entity.merchant.ApiCredential;
import com.swiftdrop.entity.merchant.Merchant;
import com.swiftdrop.exception.InvalidApiKeyException;
import com.swiftdrop.exception.MerchantNotApprovedException;
import com.swiftdrop.repository.merchant.ApiCredentialRepository;
import com.swiftdrop.repository.merchant.MerchantRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MerchantApiKeyAuthenticatorTest {
    final ApiCredentialRepository credentials = mock(ApiCredentialRepository.class);
    final MerchantRepository merchants = mock(MerchantRepository.class);
    final Instant now = Instant.parse("2026-11-07T10:00:00Z");
    final MerchantApiKeyAuthenticator authenticator =
            new MerchantApiKeyAuthenticator(credentials, merchants, Clock.fixed(now, ZoneOffset.UTC));

    final UUID merchantId = UUID.randomUUID();
    final ApiKeyGenerator.GeneratedKey generated = new ApiKeyGenerator().generate();
    final ApiCredential credential =
            ApiCredential.issue(merchantId, generated.prefix(), generated.hash(), "quote,order");

    Merchant merchant(boolean approved) {
        var merchant = Merchant.register(UUID.randomUUID(), "Shop", "shop@example.com", null, null);
        if (approved) {
            merchant.approve(UUID.randomUUID(), now);
        }
        return merchant;
    }

    void stored(Merchant merchant) {
        when(credentials.findByKeyPrefix(generated.prefix())).thenReturn(Optional.of(credential));
        when(merchants.findById(merchantId)).thenReturn(Optional.of(merchant));
    }

    @Test
    void validKeyReturnsThePrincipalWithItsScopes() {
        stored(merchant(true));

        var principal = authenticator.authenticate(generated.key());

        assertThat(principal.merchantId()).isEqualTo(merchantId);
        assertThat(principal.scopes()).isEqualTo(Set.of("quote", "order"));
    }

    @Test
    void wrongSecretForAKnownPrefixIsRejected() {
        stored(merchant(true));
        var forged = generated.prefix() + "." + "A".repeat(43);

        assertThatThrownBy(() -> authenticator.authenticate(forged)).isInstanceOf(InvalidApiKeyException.class);
    }

    @Test
    void unknownPrefixIsRejected() {
        var other = new ApiKeyGenerator().generate();

        assertThatThrownBy(() -> authenticator.authenticate(other.key())).isInstanceOf(InvalidApiKeyException.class);
    }

    @Test
    void revokedKeyIsRejectedImmediately() {
        stored(merchant(true));
        credential.revoke(now);

        assertThatThrownBy(() -> authenticator.authenticate(generated.key()))
                .isInstanceOf(InvalidApiKeyException.class);
    }

    @Test
    void missingAndMalformedKeysAreRejectedWithoutTouchingTheDatabase() {
        for (var bad : new String[] {null, "", "garbage", "Bearer " + generated.key(), generated.prefix()}) {
            assertThatThrownBy(() -> authenticator.authenticate(bad)).isInstanceOf(InvalidApiKeyException.class);
        }
        verifyNoInteractions(credentials, merchants);
    }

    @Test
    void validKeyOfAPendingBusinessIsDeniedAsNotApproved() {
        stored(merchant(false));

        assertThatThrownBy(() -> authenticator.authenticate(generated.key()))
                .isInstanceOf(MerchantNotApprovedException.class);
    }

    @Test
    void lastUsedIsRecordedOnFirstUseButNotRewrittenWithinAMinute() {
        stored(merchant(true));

        authenticator.authenticate(generated.key());
        authenticator.authenticate(generated.key());

        assertThat(credential.getLastUsedAt()).isEqualTo(now);
        verify(credentials, times(1)).save(credential);
    }
}