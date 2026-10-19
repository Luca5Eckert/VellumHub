package com.vellumhub.user_service.module.auth.infrastructure.security.token;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Public-only JWKS. Administrative key changes occur through deployment configuration, not HTTP. */
@RestController
public final class JwksController {
    private final JwtKeyRing keyRing;

    public JwksController(JwtKeyRing keyRing) {
        this.keyRing = keyRing;
    }

    @GetMapping(path = "/.well-known/jwks.json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> keys() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
                .body(Map.of("keys", keyRing.publicJwks()));
    }
}
