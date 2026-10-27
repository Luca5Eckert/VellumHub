package com.vellumhub.user_service.module.user.infrastructure.provisioning;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal network endpoint, never routed by Gateway. Auth service is the caller;
 * opaque credentials aren't bearer tokens and never appear in JWTs or logs.
 * This adapter deliberately bypasses JPA UUID generation for an already allocated ID.
 */
@RestController
@RequestMapping("/internal/identity/profiles")
@ConditionalOnProperty(name = "vellum.identity.provisioning-enabled", havingValue = "true")
public class ProfileProvisioningController {
    private final JdbcTemplate jdbc;
    private final byte[] secret;

    public ProfileProvisioningController(JdbcTemplate jdbc,
            @Value("${vellum.identity.shared-secret:}") String secret) {
        if (secret.length() < 32) {
            throw new IllegalStateException("User provisioning requires private 32+ character credential");
        }
        this.jdbc = jdbc;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Void> create(
            @RequestHeader(value = "X-Vellum-Provision-Key", required = false) String provided,
            @RequestBody NewProfile profile) {
        if (provided == null || !MessageDigest.isEqual(secret, provided.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (profile == null || profile.userId() == null || profile.userId().version() != 7
                || profile.name() == null || profile.name().isBlank() || profile.name().length() > 120
                || profile.email() == null || !profile.email().matches("(?i)^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                || profile.email().length() > 320
                || !profile.email().equals(profile.email().toLowerCase(java.util.Locale.ROOT))) {
            return ResponseEntity.badRequest().build();
        }
        // Unique ID AND case-folded email index. In case of conflict, compare actual
        // persisted values instead of treating any conflict as a successful retry.
        int inserted = jdbc.update("""
                INSERT INTO tb_users (id, name, email, active, version, created_at, updated_at)
                VALUES (?, ?, ?, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT DO NOTHING
                """, profile.userId(), profile.name().strip(), profile.email());
        if (inserted == 1) {
            return ResponseEntity.status(HttpStatus.CREATED).build();
        }
        var existing = jdbc.query("""
                SELECT name, email, active, deleted_at FROM tb_users WHERE id = ?
                """, (rs, i) -> new ProfileData(
                    rs.getString("name"), rs.getString("email"),
                    rs.getBoolean("active"), rs.getTimestamp("deleted_at") != null),
                profile.userId());
        if (existing.size() == 1 && existing.getFirst().active() && !existing.getFirst().deleted()
                && existing.getFirst().name().equals(profile.name().strip())
                && existing.getFirst().email().equals(profile.email())) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    private record ProfileData(String name, String email, boolean active, boolean deleted) {}
    public record NewProfile(UUID userId, String name, String email) {}
}
