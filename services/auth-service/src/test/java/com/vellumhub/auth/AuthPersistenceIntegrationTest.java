package com.vellumhub.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.vellumhub.auth.identity.IdentityDetailsService;
import com.vellumhub.auth.identity.IdentityRepository;
import com.vellumhub.auth.identity.UuidV7;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real PostgreSQL/Flyway/JDBC integration. Requires Docker, never an H2 substitute. */
@Testcontainers
@SpringBootTest(properties = {"spring.profiles.active=local-poc", "vellum.poc.fixture.enabled=false"})
class AuthPersistenceIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Path keyDir = makeKeys();

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url", postgres::getJdbcUrl);
        props.add("spring.datasource.username", postgres::getUsername);
        props.add("spring.datasource.password", postgres::getPassword);
        props.add("vellum.auth.keys.private-key-path", () -> keyDir.resolve("private.pem").toString());
        props.add("vellum.auth.keys.public-key-path", () -> keyDir.resolve("public.pem").toString());
        props.add("vellum.auth.keys.kid", () -> "test-key-1");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired RegisteredClientRepository clients;
    @Autowired IdentityRepository identities;
    @Autowired IdentityDetailsService details;
    @Autowired UuidV7 ids;
    @Autowired com.vellumhub.auth.identity.OnboardingIdentityRepository onboarding;

    @Test
    void persistsPublicPkceClientAndCanonicalUserIdentity() {
        var client = clients.findByClientId("vellumhub-spa-poc");
        assertThat(client).isNotNull();
        assertThat(client.getClientAuthenticationMethods())
                .containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(client.getAuthorizationGrantTypes())
                .containsExactly(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(client.getClientSecret()).isNull();
        var reloaded = new JdbcRegisteredClientRepository(jdbc)
                .findByClientId("vellumhub-spa-poc");
        assertThat(reloaded.getId()).isEqualTo(client.getId());

        var id = ids.next();
        assertThat(identities.createIfAbsent(id, "Test@Example.invalid", "{noop}test-password")).isEqualTo(1);
        assertThat(identities.createIfAbsent(ids.next(), "test@example.invalid", "{noop}other")).isZero();
        assertThat(details.loadUserByUsername("TEST@example.invalid").getUsername())
                .isEqualTo(id.toString());
    }


    @Test
    void pendingIdentityCannotAuthenticateUntilConfirmedByUserService() {
        var id = ids.next();
        var email = "pending-" + id + "@example.invalid";
        assertThat(onboarding.insertPending(id, "New User", email, "{noop}test-only"))
                .isTrue();
        var detailsBefore = details.loadUserByUsername(email);
        assertThat(detailsBefore.getUsername()).isEqualTo(id.toString());
        assertThat(detailsBefore.isEnabled()).isFalse();
        assertThat(onboarding.pending(50)).anySatisfy(p -> {
            if (p.id().equals(id)) {
                assertThat(p.displayName()).isEqualTo("New User");
            }
        });
        assertThat(onboarding.activate(id)).isTrue();
        assertThat(details.loadUserByUsername(email).isEnabled()).isTrue();
    }

    private static Path makeKeys() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            var path = Files.createTempDirectory("vellumhub-auth-poc-");
            Files.writeString(path.resolve("private.pem"), pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
            Files.writeString(path.resolve("public.pem"), pem("PUBLIC KEY", pair.getPublic().getEncoded()));
            return path;
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
            + "\n-----END " + type + "-----\n";
    }
}
