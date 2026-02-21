package com.mrs.user_service.module.auth.infrastructure.verification;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.mrs.user_service.module.auth.domain.exception.AuthDomainException;
import com.mrs.user_service.module.auth.domain.model.UserInfo;
import com.mrs.user_service.module.auth.domain.port.ExternalVerification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class GoogleExternalVerification implements ExternalVerification {

    private final GoogleIdTokenVerifier verifier;

    public GoogleExternalVerification(@Value("${google.client.id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    @Override
    public UserInfo authenticate(String token) {
        try {
            GoogleIdToken idToken = verifier.verify(token);
            if (idToken == null) {
                throw new AuthDomainException("Token externo inválido ou expirado.");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            return new UserInfo(
                    (String) payload.get("name"),
                    payload.getEmail()
            );
        } catch (AuthDomainException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthDomainException("Falha na comunicação com o provedor de identidade.");
        }
    }
}