package com.swiftdrop.config;

import com.swiftdrop.service.identity.AdminBootstrapService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdminBootstrapConfig {
    @Bean
    @ConditionalOnProperty(prefix = "swiftdrop.bootstrap", name = "admin-enabled", havingValue = "true")
    ApplicationRunner bootstrapAdmin(AdminBootstrapService service,
            @Value("${swiftdrop.bootstrap.admin-email:}") String email) {
        return args -> service.bootstrap(email);
    }
}
