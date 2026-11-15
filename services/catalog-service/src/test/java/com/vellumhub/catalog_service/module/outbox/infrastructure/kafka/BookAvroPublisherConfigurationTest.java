package com.vellumhub.catalog_service.module.outbox.infrastructure.kafka;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ProducerFactory;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class BookAvroPublisherConfigurationTest {
    @SuppressWarnings("unchecked")
    private final ProducerFactory<String, Object> producer = mock(ProducerFactory.class);

    @Test void requiresExplicitRegistryWhenBookAvroIsEnabled() {
        when(producer.getConfigurationProperties()).thenReturn(Map.of());
        assertThatThrownBy(() -> new BookAvroPublisher(producer, "", "avro"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explicit Schema Registry URL");
        assertThatThrownBy(() -> new BookAvroPublisher(producer, "http://user:pass@registry:8080", "avro"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void jsonOnlyStartupDoesNotRequireUnusedRegistry() {
        when(producer.getConfigurationProperties()).thenReturn(Map.of());
        assertThatCode(() -> {
            try (var publisher = new BookAvroPublisher(producer, "", "json")) {
                // The unused Avro producer is inert until a send.
            }
        }).doesNotThrowAnyException();
    }
}
