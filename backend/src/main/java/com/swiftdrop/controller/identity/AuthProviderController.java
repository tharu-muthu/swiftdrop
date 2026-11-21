package com.swiftdrop.controller.identity;

import com.swiftdrop.config.GoogleSettings;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthProviderController {
    private final GoogleSettings google;
    public AuthProviderController(GoogleSettings google) { this.google=google; }
    @GetMapping("/api/v1/auth/providers") public Map<String,Boolean> providers() { return Map.of("googleEnabled",google.enabled()); }
}
