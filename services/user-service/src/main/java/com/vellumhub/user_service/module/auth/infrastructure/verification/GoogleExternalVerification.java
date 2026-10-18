package com.vellumhub.user_service.module.auth.infrastructure.verification;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.vellumhub.user_service.module.auth.domain.exception.AuthDomainException;
import com.vellumhub.user_service.module.auth.domain.model.UserInfo;
import com.vellumhub.user_service.module.auth.domain.port.ExternalVerification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

@Component
public class GoogleExternalVerification implements ExternalVerification {
    private static final Set<String> ALLOWED_ISSUERS = Set.of(
            "https://accounts.google.com", "accounts.google.com");

    private final GoogleIdTokenVerifier verifier;
    private final String clientId;

    public GoogleExternalVerification(@Value("${google.client.id}") String clientId) {
        this.clientId = clientId;
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(clientId))
                .setIssuers(ALLOWED_ISSUERS)
                .build();
    }

    @Override
    public UserInfo authenticate(String token) {
        if (clientId == null || clientId.isBlank() || token == null || token.isBlank()) {
            throw new AuthDomainException("Invalid external identity.");
        }
        try {
            // GoogleIdTokenVerifier verifies signature, audience, issuer, and token time window.
            GoogleIdToken idToken = verifier.verify(token);
            if (idToken == null) {
                throw new AuthDomainException("Invalid external identity.");
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            if (!ALLOWED_ISSUERS.contains(payload.getIssuer())
                    || payload.getSubject() == null || payload.getSubject().isBlank()
                    || payload.getEmail() == null || payload.getEmail().isBlank()
                    || !Boolean.TRUE.equals(payload.getEmailVerified())
                    || payload.getExpirationTimeSeconds() == null
                    || payload.getExpirationTimeSeconds() <= System.currentTimeMillis() / 1000L) {
                throw new AuthDomainException("Invalid external identity.");
            }
            // Google accepts two canonical issuer spellings. Normalize for stable unique keys.
            return new UserInfo(
                    (String) payload.get("name"),
                    payload.getEmail(),
                    "https://accounts.google.com",
                    payload.getSubject(),
                    true
            );
        } catch (AuthDomainException ex) {
            throw ex;
        } catch (Exception ex) {
            // Never include the raw provider token or Google exception details in responses/logs.
            throw new AuthDomainException("Invalid external identity.");
        }
    }
}
