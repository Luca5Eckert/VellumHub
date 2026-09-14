package com.vellumhub.e2e.support;

import com.vellumhub.testing.distributed.container.KafkaPgvectorIntegrationTestSupport;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class RecommendationE2ETestSupport extends KafkaPgvectorIntegrationTestSupport {

    private static final String JWT_SECRET =
            "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLXB1cnBvc2VzLXdpdGgtYXQtbGVhc3QtMjU2LWJpdHM=";
    private static final String PRODUCER_TYPE_MAPPINGS = String.join(",",
            "create_book_event:com.vellumhub.kafka.contracts.book.CreateBookEvent",
            "create_rating_event:com.vellumhub.kafka.contracts.engagement.CreatedRatingEvent"
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
        registry.add("spring.flyway.locations", () -> "classpath:recommendation/db/migration");

        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.admin.fail-fast", () -> "true");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.producer.key-serializer", () -> StringSerializer.class.getName());
        registry.add("spring.kafka.producer.value-serializer", () -> JsonSerializer.class.getName());
        registry.add("spring.kafka.producer.properties.spring.json.add.type.headers", () -> "true");
        registry.add("spring.kafka.producer.properties.spring.json.type.mapping", () -> PRODUCER_TYPE_MAPPINGS);

        registry.add("app.kafka.retry.backoff-ms", () -> "100");
        registry.add("management.health.kafka.enabled", () -> "false");
        registry.add("jwt.secret", () -> JWT_SECRET);
        registry.add("server.port", () -> "0");

        registry.add("catalog-service.ribbon.listOfServers", () -> "http://127.0.0.1:1");
        registry.add("user-service.ribbon.listOfServers", () -> "http://127.0.0.1:1");
        registry.add("engagement-service.ribbon.listOfServers", () -> "http://127.0.0.1:1");
    }
}
