package com.vellumhub.user_service.module.user_preference.infrastructure;

import com.vellumhub.kafka.avro.UserPreferenceAvroMapper;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.user.CreateUserPreferenceEvent;
import com.vellumhub.kafka.contracts.user.UserPreferenceChanged;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

@Component
class PreferenceAvroPublisher {
    private final DefaultKafkaProducerFactory<String, Object> factory;
    private final KafkaTemplate<String, Object> template;

    PreferenceAvroPublisher(ProducerFactory<String, CreateUserPreferenceEvent> existing,
                            @Value("${app.preference-outbox.registry-url:http://127.0.0.1:18081/apis/ccompat/v7}")
                            String registryUrl) {
        if (!registryUrl.startsWith("http://") && !registryUrl.startsWith("https://")) {
            throw new IllegalArgumentException("Invalid Schema Registry URL");
        }
        var properties = new HashMap<>(existing.getConfigurationProperties());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        properties.put("schema.registry.url", registryUrl);
        properties.put("auto.register.schemas", false);
        properties.put("use.latest.version", false);
        properties.put("avro.remove.java.properties", true);
        properties.put("value.subject.name.strategy", "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy");
        factory = new DefaultKafkaProducerFactory<>(properties);
        template = new KafkaTemplate<>(factory);
    }

    CompletableFuture<SendResult<String, Object>> send(UserPreferenceChanged event) {
        return template.send(KafkaTopics.USER_PREFERENCE_EVENTS_AVRO, event.userId().toString(),
                UserPreferenceAvroMapper.toAvro(event));
    }

    @PreDestroy
    void close() {
        factory.destroy();
    }
}
