package com.vellumhub.auth.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityRepository {
    private final JdbcTemplate jdbc;

    public IdentityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Identity> findByLogin(String login) {
        return jdbc.query("""
                SELECT id, login, password_hash, role, active
                FROM auth_identity WHERE lower(login) = lower(?)
                """,
                (rs, ignored) -> new Identity(
                        rs.getObject("id", UUID.class),
                        rs.getString("login"),
                        rs.getString("password_hash"),
                        IdentityRole.valueOf(rs.getString("role")),
                        rs.getBoolean("active")),
                login.strip()).stream().findFirst();
    }

    public int createIfAbsent(UUID id, String login, String encodedPassword, IdentityRole role) {
        return jdbc.update("""
                INSERT INTO auth_identity (id, login, password_hash, role, active)
                VALUES (?, lower(?), ?, ?, true)
                ON CONFLICT DO NOTHING
                """, id, login.strip(), encodedPassword, role.name());
    }

    public Optional<Identity> findById(UUID id) {
        return jdbc.query("""
                SELECT id, login, password_hash, role, active FROM auth_identity WHERE id = ?
                """, (rs, row) -> new Identity(rs.getObject("id", UUID.class),
                        rs.getString("login"), rs.getString("password_hash"),
                        IdentityRole.valueOf(rs.getString("role")),
                        rs.getBoolean("active")), id).stream().findFirst();
    }

    @org.springframework.transaction.annotation.Transactional
    public void linkVerifiedGoogle(UUID userId, String issuer, String subject) {
        int inserted = jdbc.update("""
                INSERT INTO auth_external_identity (issuer, subject, user_id)
                VALUES (?, ?, ?) ON CONFLICT DO NOTHING
                """, issuer, subject, userId);
        if (inserted == 0) {
            var existing = jdbc.query("""
                    SELECT user_id FROM auth_external_identity WHERE issuer = ? AND subject = ?
                    """, (rs, row) -> rs.getObject(1, UUID.class), issuer, subject);
            // A replay for the same binding is idempotent; a different owner is never reassigned.
            if (existing.isEmpty() || !existing.getFirst().equals(userId)) {
                throw new FederatedIdentityRepository.FederationConflictException();
            }
        }
    }

    public record Identity(UUID id, String login, String passwordHash, IdentityRole role, boolean active) { }
}
