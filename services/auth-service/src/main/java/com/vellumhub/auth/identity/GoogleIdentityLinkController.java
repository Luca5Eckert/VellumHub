package com.vellumhub.auth.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.*;

/**
 * Link only after proving a local password and a fresh nonce-bound, signed Google ID Token.
 * The challenge and link POSTs are session-authenticated and CSRF-protected by Spring Security.
 */
@RestController
@RequestMapping("/v1/identity/google")
@ConditionalOnProperty(name = "vellum.google.enabled", havingValue = "true")
public class GoogleIdentityLinkController {
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private final IdentityRepository identities;
    private final JwtDecoder googleTokens;
    private final PasswordEncoder passwords;
    private final String googleClientId;
    private final GoogleLinkChallengeRepository challenges;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock = Clock.systemUTC();

    public GoogleIdentityLinkController(IdentityRepository identities,
            @Qualifier("googleIdTokenDecoder") JwtDecoder googleTokens,
            PasswordEncoder passwords,
            @Value("${spring.security.oauth2.client.registration.google.client-id}") String googleClientId,
            GoogleLinkChallengeRepository challenges) {
        this.identities = identities;
        this.googleTokens = googleTokens;
        this.passwords = passwords;
        this.googleClientId = googleClientId;
        this.challenges = challenges;
    }

    @PostMapping("/link/challenge")
    public ResponseEntity<Map<String, String>> challenge(
            @RequestBody PasswordProof proof, Authentication authentication, HttpServletRequest request) {
        UUID userId = localUserId(authentication);
        var identity = identities.findById(userId).filter(IdentityRepository.Identity::active)
                .orElseThrow(LinkForbiddenException::new);
        if (identity.passwordHash() == null || proof.currentPassword() == null
                || !passwords.matches(proof.currentPassword(), identity.passwordHash())) {
            throw new LinkForbiddenException();
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        challenges.issue(request.getSession(true).getId(), userId, nonce, clock.instant().plus(CHALLENGE_TTL));
        return ResponseEntity.ok(Map.of("nonce", nonce));
    }

    @PostMapping("/link")
    public ResponseEntity<Void> link(@RequestBody GoogleIdTokenProof proof,
            Authentication authentication, HttpServletRequest request) {
        UUID userId = localUserId(authentication);
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new LinkForbiddenException();
        }
        String nonce = challenges.consume(session.getId(), userId);
        if (nonce == null
                || proof.idToken() == null || proof.idToken().isBlank()) {
            throw new LinkForbiddenException();
        }
        try {
            Jwt token = googleTokens.decode(proof.idToken());
            if (!token.getAudience().contains(googleClientId)
                    || !nonce.equals(token.getClaimAsString("nonce"))
                    || !"https://accounts.google.com".equals(token.getIssuer().toString())
                    || !Boolean.TRUE.equals(token.getClaim("email_verified"))) {
                throw new LinkForbiddenException();
            }
            String subject = token.getSubject();
            if (subject == null || subject.isBlank() || subject.length() > 255) {
                throw new LinkForbiddenException();
            }
            identities.findById(userId).filter(IdentityRepository.Identity::active)
                    .orElseThrow(LinkForbiddenException::new);
            identities.linkVerifiedGoogle(userId, "https://accounts.google.com", subject);
            return ResponseEntity.noContent().build();
        } catch (JwtException | IllegalArgumentException exception) {
            throw new LinkForbiddenException();
        }
    }

    private UUID localUserId(Authentication authentication) {
        if (!(authentication instanceof UsernamePasswordAuthenticationToken)
                || !authentication.isAuthenticated()) {
            throw new LinkForbiddenException();
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new LinkForbiddenException();
        }
    }

    @ExceptionHandler(LinkForbiddenException.class)
    ResponseEntity<Void> forbidden() { return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); }

    @ExceptionHandler(FederatedIdentityRepository.FederationConflictException.class)
    ResponseEntity<Void> conflict() { return ResponseEntity.status(HttpStatus.CONFLICT).build(); }

    public record PasswordProof(String currentPassword) { }
    public record GoogleIdTokenProof(String idToken) { }
    static class LinkForbiddenException extends RuntimeException { }
}
