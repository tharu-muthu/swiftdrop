package com.swiftdrop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("swiftdrop.google")
public record GoogleSettings(String frontendOrigin) {
    public GoogleSettings {
        if (frontendOrigin == null || frontendOrigin.isBlank())
            frontendOrigin = "http://127.0.0.1:5173";
    }
}

