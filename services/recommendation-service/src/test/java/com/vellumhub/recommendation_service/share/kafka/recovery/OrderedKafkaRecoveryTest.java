package com.vellumhub.recommendation_service.share.kafka.recovery;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OrderedKafkaRecoveryTest {
    private OrderedKafkaRecovery recovery(long backoff, long retries) {
        return new OrderedKafkaRecovery(mock(JdbcTemplate.class),
                mock(KafkaTemplate.class), mock(KafkaListenerEndpointRegistry.class),
                new SimpleMeterRegistry(), "recommendation-service", backoff, retries);
    }

    @Test
    void validatesBoundedRetryPolicy() {
        assertThatThrownBy(() -> recovery(0, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recovery(1000, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recovery(1000, 21)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void installsFailClosedErrorHandlerAndRebalanceRecovery() {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(mock(ConsumerFactory.class));
        recovery(1, 3).configure(factory, "recommendation-service");
        assertThat(factory.getRecordInterceptor()).isNotNull();
        assertThat(factory.getContainerProperties().getConsumerRebalanceListener()).isNotNull();
    }
}
