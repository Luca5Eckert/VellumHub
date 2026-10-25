package com.vellumhub.auth.identity;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local Auth transaction ONLY: creates a disabled pending identity and its verified provider
 * subject atomically. The User profile is provisioned separately and idempotently.
 */
@Repository
public class FederatedIdentityRepository {
    private final JdbcTemplate jdbc;

    public FederatedIdentityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public UUID resolveOrCreate(String issuer, String subject, String email, String name, UUID proposedId) {
        var existing = findLinked(issuer, subject);
        if (existing != null) {
            return existing;
        }
        // Never claim an existing local account merely because the verified email is equal.
        int created = jdbc.update("""
                INSERT INTO auth_identity (id, login, password_hash, active, provisioning_state, display_name)
                VALUES (?, ?, NULL, false, 'PENDING_PROFILE', ?)
                ON CONFLICT DO NOTHING
                """, proposedId, email, name);
        if (created == 0) {
            var raced = findLinked(issuer, subject);
            if (raced != null) {
                return raced;
            }
            throw new FederationConflictException();
        }
        int linked = jdbc.update("""
                INSERT INTO auth_external_identity (issuer, subject, user_id)
                VALUES (?, ?, ?) ON CONFLICT DO NOTHING
                """, issuer, subject, proposedId);
        if (linked != 1) {
            // Roll back the new auth_identity, do not leave an orphan pending account.
            throw new FederationConflictException();
        }
        return proposedId;
    }

    public UUID findLinked(String issuer, String subject) {
        List<UUID> ids = jdbc.query("""
                SELECT user_id FROM auth_external_identity WHERE issuer = ? AND subject = ?
                """, (rs, row) -> rs.getObject(1, UUID.class), issuer, subject);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    public IdentityState state(UUID id) {
        return jdbc.query("""
                SELECT active, provisioning_state, login, display_name
                FROM auth_identity WHERE id = ?
                """, (rs, row) -> new IdentityState(rs.getBoolean("active"),
                        rs.getString("provisioning_state"), rs.getString("login"),
                        rs.getString("display_name")), id).stream().findFirst()
                .orElseThrow(FederationConflictException::new);
    }

    public int activate(UUID id) {
        return jdbc.update("""
                UPDATE auth_identity SET active = true, provisioning_state = 'ACTIVE'
                WHERE id = ? AND provisioning_state = 'PENDING_PROFILE'
                """, id);
    }

    public record IdentityState(boolean active, String provisioning, String email, String name) { }

    public static class FederationConflictException extends RuntimeException { }
}
