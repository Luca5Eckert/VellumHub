package com.vellumhub.auth.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Each call is its own local JDBC transaction. Never hold a DB lock over network I/O. */
@Repository
public class OnboardingIdentityRepository {
    private final JdbcTemplate jdbc;

    public OnboardingIdentityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean insertPending(UUID id, String name, String email, String encodedPassword) {
        return jdbc.update("""
                INSERT INTO auth_identity
                    (id, display_name, login, password_hash, role, active, provisioning_state)
                VALUES (?, ?, ?, ?, 'USER', false, 'PENDING_PROFILE')
                ON CONFLICT DO NOTHING
                """, id, name, email, encodedPassword) == 1;
    }

    public boolean activate(UUID id) {
        return jdbc.update("""
                UPDATE auth_identity SET active = true, provisioning_state = 'ACTIVE'
                WHERE id = ? AND provisioning_state = 'PENDING_PROFILE'
                """, id) == 1;
    }

    public List<PendingIdentity> pending(int limit) {
        return jdbc.query("""
                SELECT id, display_name, login FROM auth_identity
                WHERE provisioning_state = 'PENDING_PROFILE'
                ORDER BY created_at, id LIMIT ?
                """, (rs, row) -> new PendingIdentity(
                        rs.getObject("id", UUID.class),
                        rs.getString("display_name"), rs.getString("login")), limit);
    }

    public record PendingIdentity(UUID id, String displayName, String email) {}
}
