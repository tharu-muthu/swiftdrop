package com.swiftdrop.dto.identity;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    @Override public String toString() { return "TokenResponse[redacted]"; }
}
