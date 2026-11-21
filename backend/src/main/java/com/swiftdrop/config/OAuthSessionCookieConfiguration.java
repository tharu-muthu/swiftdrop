package com.swiftdrop.config;

import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.*;

@Configuration
public class OAuthSessionCookieConfiguration {
    @Bean ServletContextInitializer oauthSessionCookies(AuthSettings auth) {
        return context->{
            var cookie=context.getSessionCookieConfig(); cookie.setHttpOnly(true); cookie.setSecure(!auth.localhostDevelopment());
        };
    }
}
