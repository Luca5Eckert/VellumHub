package com.vellumhub.catalog_service.architecture;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AFF-DATA-004: negative authorization proof on a real PostgreSQL instance.
 *
 * This proves the intended database role isolation mechanism, not that every
 * deployment already provisions distinct roles. Compose credentials must be
 * inspected independently before the overall AFF is considered enforced.
 */
@Testcontainers
@Tag("distributed")
class DatabaseOwnershipPostgresFitnessTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine");

    @Test
    void domainRoleCanReadOwnSchemaButNotAnotherDomain() throws Exception {
        String role = "catalog_reader_" + UUID.randomUUID().toString().replace("-", "");
        // The role is intentionally not a database owner, superuser or member
        // of another service role.
        try (var admin = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statements = admin.createStatement()) {
            statements.execute("CREATE SCHEMA catalog_owned");
            statements.execute("CREATE SCHEMA engagement_owned");
            statements.execute("CREATE TABLE catalog_owned.books (id bigint)");
            statements.execute("CREATE TABLE engagement_owned.ratings (id bigint)");
            statements.execute("INSERT INTO catalog_owned.books VALUES (1)");
            statements.execute("INSERT INTO engagement_owned.ratings VALUES (2)");
            statements.execute("CREATE ROLE " + role + " LOGIN PASSWORD 'isolated-test-password'");
            statements.execute("GRANT USAGE ON SCHEMA catalog_owned TO " + role);
            statements.execute("GRANT SELECT ON catalog_owned.books TO " + role);
        }

        try (var restricted = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), role, "isolated-test-password");
             var statements = restricted.createStatement()) {
            try (var rs = statements.executeQuery("SELECT count(*) FROM catalog_owned.books")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
            // PostgreSQL insufficient_privilege. No mock/SQL parser can stand
            // in for the server's actual authorization decision here.
            assertThatThrownBy(() -> statements.executeQuery(
                    "SELECT count(*) FROM engagement_owned.ratings"))
                    .isInstanceOf(SQLException.class)
                    .extracting(error -> ((SQLException) error).getSQLState())
                    .isEqualTo("42501");
        }
    }
}
