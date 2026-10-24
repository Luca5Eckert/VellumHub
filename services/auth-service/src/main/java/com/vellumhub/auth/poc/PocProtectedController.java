package com.vellumhub.auth.poc;

import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** PoC-only API protected independently with the public JWKS and strict JWT contract. */
@RestController
public class PocProtectedController {
    @GetMapping("/poc/api/me")
    public Map<String, String> me(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
                "sub", jwt.getSubject(),
                "client_id", jwt.getClaimAsString("client_id"),
                "issuer", jwt.getIssuer().toString());
    }
}
