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
                SELECT id, login, password_hash, active
                FROM auth_identity WHERE lower(login) = lower(?)
                """,
                (rs, ignored) -> new Identity(
                        rs.getObject("id", UUID.class),
                        rs.getString("login"),
                        rs.getString("password_hash"),
                        rs.getBoolean("active")),
                login.strip()).stream().findFirst();
    }

    public int createIfAbsent(UUID id, String login, String encodedPassword) {
        return jdbc.update("""
                INSERT INTO auth_identity (id, login, password_hash, active)
                VALUES (?, lower(?), ?, true)
                ON CONFLICT DO NOTHING
                """, id, login.strip(), encodedPassword);
    }

    public record Identity(UUID id, String login, String passwordHash, boolean active) { }
}
