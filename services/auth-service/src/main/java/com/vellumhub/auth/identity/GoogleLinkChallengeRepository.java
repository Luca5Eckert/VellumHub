package com.vellumhub.auth.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Database consumption is atomic across requests and replicas; never store a raw session cookie. */
@Repository
public class GoogleLinkChallengeRepository {
    private final JdbcTemplate jdbc;

    public GoogleLinkChallengeRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void issue(String sessionId, UUID userId, String nonce, Instant expiresAt) {
        jdbc.update("DELETE FROM auth_google_link_challenge WHERE expires_at <= CURRENT_TIMESTAMP");
        jdbc.update("""
                INSERT INTO auth_google_link_challenge (session_hash, user_id, nonce, expires_at)
                VALUES (?, ?, ?, ?) ON CONFLICT (session_hash)
                DO UPDATE SET user_id = EXCLUDED.user_id, nonce = EXCLUDED.nonce,
                              expires_at = EXCLUDED.expires_at
                """, hash(sessionId), userId, nonce, java.sql.Timestamp.from(expiresAt));
    }

    /** Delete first, including expired challenges. Exactly one contender can obtain the nonce. */
    public String consume(String sessionId, UUID userId) {
        var matches = jdbc.query("""
                DELETE FROM auth_google_link_challenge WHERE session_hash = ? AND user_id = ?
                RETURNING nonce, expires_at > CURRENT_TIMESTAMP AS valid
                """, (rs, row) -> new Challenge(rs.getString("nonce"), rs.getBoolean("valid")),
                hash(sessionId), userId);
        return matches.isEmpty() || !matches.getFirst().valid() ? null : matches.getFirst().nonce();
    }

    private record Challenge(String nonce, boolean valid) {}

    private static String hash(String sessionId) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(sessionId.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
