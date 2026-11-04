package com.swiftdrop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// Accounts come from our identity service; never generate a default framework user/password.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SwiftDropApplication {
    public static void main(String[] args) {
        SpringApplication.run(SwiftDropApplication.class, args);
    }
}
