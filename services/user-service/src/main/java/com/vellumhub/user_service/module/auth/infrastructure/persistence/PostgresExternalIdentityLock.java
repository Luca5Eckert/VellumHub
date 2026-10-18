package com.vellumhub.user_service.module.auth.infrastructure.persistence;

import com.vellumhub.user_service.module.auth.domain.port.ExternalIdentityLock;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;

@Component
public class PostgresExternalIdentityLock implements ExternalIdentityLock {
    private final JdbcTemplate jdbc;

    public PostgresExternalIdentityLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void acquire(String... keys) {
        // Callers hold a Spring transaction. Deterministic acquisition prevents deadlocks.
        Arrays.stream(keys).distinct().sorted().forEach(this::acquireOne);
    }

    private void acquireOne(String key) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            String database = connection.getMetaData().getDatabaseProductName();
            if ("H2".equalsIgnoreCase(database)) {
                // Non-production local H2 profile cannot provide cross-instance locking.
                return null;
            }
            if (!"PostgreSQL".equalsIgnoreCase(database)) {
                throw new SQLException("External identity locking requires PostgreSQL");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, "vellumhub:external-identity:" + key);
                statement.execute();
            }
            return null;
        });
    }
}
