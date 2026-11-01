package com.swiftdrop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// Identity is a later feature: do not generate a default user or password.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SwiftDropApplication {
    public static void main(String[] args) {
        SpringApplication.run(SwiftDropApplication.class, args);
    }
}
