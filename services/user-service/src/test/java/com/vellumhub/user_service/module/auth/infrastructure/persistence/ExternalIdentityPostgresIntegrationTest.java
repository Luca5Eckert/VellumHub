package com.vellumhub.user_service.module.auth.infrastructure.persistence;

import com.vellumhub.user_service.UserServiceApplication;
import com.vellumhub.user_service.share.security.token.TestRsaKeyMaterial;
import com.vellumhub.user_service.module.auth.domain.exception.ExternalIdentityConflictException;
import com.vellumhub.user_service.module.auth.domain.model.UserInfo;
import com.vellumhub.user_service.module.auth.domain.service.ExternalIdentityBindingService;
import com.vellumhub.user_service.module.user.domain.RoleUser;
import com.vellumhub.user_service.module.user.domain.UserEntity;
import com.vellumhub.user_service.module.user.domain.port.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(classes = UserServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ExternalIdentityPostgresIntegrationTest {
    private static final String ISSUER = "https://accounts.google.com";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("external_identity_test").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:65535");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("management.health.kafka.enabled", () -> "false");
        registry.add("jwt.public-key-location", TestRsaKeyMaterial::publicLocation);
        registry.add("jwt.private-key-location", TestRsaKeyMaterial::privateLocation);
        registry.add("jwt.expiration-ms", () -> "3600000");
        registry.add("google.client.id", () -> "test-client");
    }

    @Autowired ExternalIdentityBindingService bindings;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    private UserInfo identity(String subject, String email) {
        return new UserInfo("User", email, ISSUER, subject, true);
    }

    @Test
    void flywayCreatesUniqueIndexesAndAllowsGoogleOnlyAccounts() {
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '2' and success",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from pg_indexes where indexname = 'uk_external_identity_provider_subject'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from pg_indexes where indexname = 'uk_external_identity_user_provider'",
                Integer.class)).isEqualTo(1);

        String email = "google-only-" + UUID.randomUUID() + "@example.com";
        String subject = UUID.randomUUID().toString();
        UserEntity user = bindings.resolveForLogin(identity(subject, email));
        assertThat(user.getPassword()).isNull();
        assertThat(bindings.resolveForLogin(identity(subject, email))).extracting(UserEntity::getId)
                .isEqualTo(user.getId());
    }

    @Test
    void existingLocalAccountNeverAutoLinksByEmail() {
        String email = "legacy-" + UUID.randomUUID() + "@example.com";
        UserEntity local = users.saveAndFlush(UserEntity.builder()
                .name("Legacy").email(email).password(encoder.encode("local-password"))
                .role(RoleUser.USER).active(true).build());
        UserInfo google = identity(UUID.randomUUID().toString(), email);
        assertThatThrownBy(() -> bindings.resolveForLogin(google))
                .isInstanceOf(ExternalIdentityConflictException.class);

        bindings.link(local.getId(), google, "local-password");
        assertThat(bindings.resolveForLogin(google).getId()).isEqualTo(local.getId());
        bindings.unlink(local.getId(), "local-password");
        assertThatThrownBy(() -> bindings.resolveForLogin(google))
                .isInstanceOf(ExternalIdentityConflictException.class);
        assertThat(jdbc.queryForObject(
                "select count(*) from external_identity_audit where user_id = ?",
                Integer.class, local.getId())).isEqualTo(2);
    }

    @Test
    void softDeletedLocalAccountIsNotRecreatedFromMatchingProviderEmail() {
        String email = "deleted-" + UUID.randomUUID() + "@example.com";
        UserEntity owner = users.saveAndFlush(UserEntity.builder()
                .name("Legacy deleted").email(email).password(encoder.encode("old-password"))
                .role(RoleUser.USER).active(true).build());
        jdbc.update("UPDATE tb_users SET deleted_at = CURRENT_TIMESTAMP, active = false WHERE id = ?", owner.getId());
        assertThatThrownBy(() -> bindings.resolveForLogin(identity(UUID.randomUUID().toString(), email)))
                .isInstanceOf(ExternalIdentityConflictException.class);
    }

    @Test
    void concurrentLoginForOneSubjectResolvesOneUserAndOneAuditEvent() throws Exception {
        String subject = UUID.randomUUID().toString();
        String email = "race-" + UUID.randomUUID() + "@example.com";
        UserInfo verified = identity(subject, email);
        CountDownLatch go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { go.await(); return bindings.resolveForLogin(verified).getId(); });
            var second = pool.submit(() -> { go.await(); return bindings.resolveForLogin(verified).getId(); });
            go.countDown();
            UUID idA = first.get(25, TimeUnit.SECONDS);
            UUID idB = second.get(25, TimeUnit.SECONDS);
            assertThat(idA).isEqualTo(idB);
            assertThat(jdbc.queryForObject(
                    "select count(*) from external_identities where issuer = ? and subject = ?",
                    Integer.class, ISSUER, subject)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "select count(*) from external_identity_audit where user_id = ?",
                    Integer.class, idA)).isEqualTo(1);
        }
    }

    @Test
    void subjectIsUniqueAcrossDifferentUsersEvenWithMatchingClaimEmail() {
        String emailA = "one-" + UUID.randomUUID() + "@example.com";
        String emailB = "two-" + UUID.randomUUID() + "@example.com";
        String subject = UUID.randomUUID().toString();
        UserEntity one = users.saveAndFlush(UserEntity.builder()
                .name("One").email(emailA).password(encoder.encode("first-secret"))
                .role(RoleUser.USER).active(true).build());
        UserEntity two = users.saveAndFlush(UserEntity.builder()
                .name("Two").email(emailB).password(encoder.encode("second-secret"))
                .role(RoleUser.USER).active(true).build());

        bindings.link(one.getId(), identity(subject, emailA), "first-secret");
        assertThatThrownBy(() -> bindings.link(two.getId(), identity(subject, emailB), "second-secret"))
                .isInstanceOf(ExternalIdentityConflictException.class);
    }
}
