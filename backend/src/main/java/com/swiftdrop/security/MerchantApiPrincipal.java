package com.swiftdrop.security;

import java.util.Set;
import java.util.UUID;

/** Identity established by a valid merchant API key. Never built from request content. */
public record MerchantApiPrincipal(UUID merchantId, UUID credentialId, Set<String> scopes) { }