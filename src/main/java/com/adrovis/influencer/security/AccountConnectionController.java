package com.adrovis.influencer.security;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "adrovisBearer")
public class AccountConnectionController {

    @GetMapping("/api/v1/account/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("success", true, "accountId", jwt.getSubject(), "roles", jwt.getClaimAsStringList("roles"));
    }
}
