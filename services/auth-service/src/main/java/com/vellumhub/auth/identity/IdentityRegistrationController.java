package com.vellumhub.auth.identity;

import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/identity")
@ConditionalOnProperty(name = "vellum.identity.registration-enabled", havingValue = "true")
public class IdentityRegistrationController {
    private final IdentityRegistrationService registration;

    public IdentityRegistrationController(IdentityRegistrationService registration) {
        this.registration = registration;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, UUID>> register(@RequestBody NewIdentity input) {
        UUID id = registration.register(input.name(), input.email(), input.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("userId", id));
    }

    @ExceptionHandler(IdentityRegistrationService.InvalidRegistrationException.class)
    public ResponseEntity<Void> invalid() {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(IdentityRegistrationService.IdentityAlreadyExistsException.class)
    public ResponseEntity<Void> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(IdentityRegistrationService.ProvisioningPendingException.class)
    public ResponseEntity<Void> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    public record NewIdentity(String name, String email, String password) {}
}
