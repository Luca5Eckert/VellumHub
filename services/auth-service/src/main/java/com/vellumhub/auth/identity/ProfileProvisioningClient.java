package com.vellumhub.auth.identity;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Network call outside JDBC transactions; the same userId is its idempotency key. */
@Component
@ConditionalOnProperty(name = "vellum.identity.registration-enabled", havingValue = "true")
public class ProfileProvisioningClient {
    private final RestClient client;

    public ProfileProvisioningClient(
            @Value("${vellum.identity.user-service-url:}") String url,
            @Value("${vellum.identity.shared-secret:}") String secret) {
        if (url.isBlank() || secret.length() < 32 || !url.startsWith("http")) {
            throw new IllegalStateException("Registration requires user-service URL and a 32+ char private credential");
        }
        this.client = RestClient.builder()
                .baseUrl(url)
                .defaultHeader("X-Vellum-Provision-Key", secret)
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(Duration.ofSeconds(2));
                    setReadTimeout(Duration.ofSeconds(3));
                }})
                .build();
    }

    public void create(UUID id, String name, String email) {
        client.post().uri("/internal/identity/profiles")
                .body(new ProvisionProfile(id, name, email))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, response) -> {
                    throw new ProvisioningException("User profile provisioning rejected: " + response.getStatusCode().value());
                })
                .toBodilessEntity();
    }

    public record ProvisionProfile(UUID userId, String name, String email) {}
    public static class ProvisioningException extends RuntimeException {
        public ProvisioningException(String reason) { super(reason); }
    }
}
