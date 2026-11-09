package com.swiftdrop.service.merchant;

import com.swiftdrop.entity.merchant.ApiCredential;
import com.swiftdrop.entity.merchant.MerchantApprovalStatus;
import com.swiftdrop.exception.InvalidApiKeyException;
import com.swiftdrop.exception.MerchantNotApprovedException;
import com.swiftdrop.repository.merchant.ApiCredentialRepository;
import com.swiftdrop.repository.merchant.MerchantRepository;
import com.swiftdrop.security.MerchantApiPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantApiKeyAuthenticator {
    private static final Pattern KEY_FORMAT = Pattern.compile("^(sdk_[0-9a-f]{10})\\.[A-Za-z0-9_-]{43}$");
    private static final String NO_MATCH_HASH = "0".repeat(64);
    private static final Duration LAST_USED_GRANULARITY = Duration.ofMinutes(1);

    private final ApiCredentialRepository credentials;
    private final MerchantRepository merchants;
    private final Clock clock;

    public MerchantApiKeyAuthenticator(ApiCredentialRepository credentials, MerchantRepository merchants, Clock clock) {
        this.credentials = credentials;
        this.merchants = merchants;
        this.clock = clock;
    }

    @Transactional
    public MerchantApiPrincipal authenticate(String presentedKey) {
        if (presentedKey == null) {
            throw new InvalidApiKeyException();
        }
        var format = KEY_FORMAT.matcher(presentedKey);
        if (!format.matches()) {
            throw new InvalidApiKeyException();
        }
        var credential = credentials.findByKeyPrefix(format.group(1)).orElse(null);
        // Always hash and compare, even for an unknown prefix, so timing does not reveal which prefixes exist.
        var presentedHash = ApiKeyGenerator.sha256Hex(presentedKey);
        var expectedHash = credential == null ? NO_MATCH_HASH : credential.getSecretHash();
        var hashMatches = MessageDigest.isEqual(
                presentedHash.getBytes(StandardCharsets.UTF_8), expectedHash.getBytes(StandardCharsets.UTF_8));
        if (credential == null || !hashMatches || credential.isRevoked()) {
            throw new InvalidApiKeyException();
        }
        var merchant = merchants.findById(credential.getMerchantId()).orElseThrow(InvalidApiKeyException::new);
        if (merchant.getApprovalStatus() != MerchantApprovalStatus.APPROVED) {
            throw new MerchantNotApprovedException();
        }
        recordUse(credential);
        return new MerchantApiPrincipal(credential.getMerchantId(), credential.getId(), credential.scopeSet());
    }

    private void recordUse(ApiCredential credential) {
        var now = clock.instant();
        var last = credential.getLastUsedAt();
        if (last == null || last.isBefore(now.minus(LAST_USED_GRANULARITY))) {
            credential.markUsed(now);
            credentials.save(credential);
        }
    }
}