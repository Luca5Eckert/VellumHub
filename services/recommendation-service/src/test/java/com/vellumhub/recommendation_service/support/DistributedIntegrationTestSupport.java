package com.vellumhub.recommendation_service.support;

import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

@Testcontainers
public abstract class DistributedIntegrationTestSupport {

    protected static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(15);
    protected static final Duration ASYNC_POLL_INTERVAL = Duration.ofMillis(100);

    private static final String JWT_SECRET =
            "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLXB1cnBvc2VzLXdpdGgtYXQtbGVhc3QtMjU2LWJpdHM=";
    private static final String CREATE_BOOK_TYPE_MAPPING =
            "create_book_event:com.vellumhub.kafka.contracts.book.CreateBookEvent";

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg15").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("recommendation_distributed_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    protected static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );

    @DynamicPropertySource
    static void configureDistributedDependencies(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");

        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.admin.fail-fast", () -> "true");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.producer.key-serializer", () -> StringSerializer.class.getName());
        registry.add("spring.kafka.producer.value-serializer", () -> JsonSerializer.class.getName());
        registry.add("spring.kafka.producer.properties.spring.json.add.type.headers", () -> "true");
        registry.add("spring.kafka.producer.properties.spring.json.type.mapping", () -> CREATE_BOOK_TYPE_MAPPING);

        registry.add("app.kafka.retry.backoff-ms", () -> "100");
        registry.add("management.health.kafka.enabled", () -> "false");
        registry.add("jwt.secret", () -> JWT_SECRET);
        registry.add("server.port", () -> "0");
    }
}
