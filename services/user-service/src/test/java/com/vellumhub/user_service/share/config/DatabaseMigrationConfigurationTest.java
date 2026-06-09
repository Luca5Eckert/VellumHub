package com.vellumhub.user_service.share.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMigrationConfigurationTest {

    @Test
    void flywayIsAvailableOnClasspath() {
        assertThat(ClassUtils.isPresent("org.flywaydb.core.Flyway", getClass().getClassLoader())).isTrue();
    }

    @Test
    void flywayAutoConfigurationIsAvailableOnClasspath() {
        assertThat(ClassUtils.isPresent("org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration", getClass().getClassLoader())).isTrue();
    }

    @Test
    void initialFlywayMigrationIsPackaged() throws IOException {
        String migration = readResource("db/migration/V1__create_initial_schema.sql");

        assertThat(migration)
                .contains("CREATE TABLE tb_users")
                .contains("CREATE TABLE user_preferences")
                .contains("CREATE TABLE user_preference_genres");
    }

    @Test
    void productionProfileValidatesSchemaInsteadOfUpdatingIt() throws IOException {
        Properties properties = loadProperties("application-prod.properties");

        assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @Test
    void h2LocalProfileDoesNotRunPostgresMigrations() throws IOException {
        Properties properties = loadProperties("application-local.properties");

        assertThat(properties.getProperty("spring.flyway.enabled")).isEqualTo("false");
    }

    private Properties loadProperties(String path) throws IOException {
        Properties properties = new Properties();

        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            properties.load(inputStream);
        }

        return properties;
    }

    private String readResource(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        assertThat(resource.exists()).isTrue();

        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
