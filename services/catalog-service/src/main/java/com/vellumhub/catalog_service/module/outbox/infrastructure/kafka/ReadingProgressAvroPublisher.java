package com.vellumhub.catalog_service.module.outbox.infrastructure.kafka;

import com.vellumhub.kafka.avro.ReadingProgressAvroMapper;
import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
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

/** Isolated Avro serializer inheriting the JSON producer's broker security and ACK policy. */
@Component
public class ReadingProgressAvroPublisher implements AutoCloseable {
    private final DefaultKafkaProducerFactory<String,Object> factory;
    private final KafkaTemplate<String,Object> template;

    public ReadingProgressAvroPublisher(ProducerFactory<String,Object> existing,
            @Value("${app.reading-progress.avro.registry-url:http://127.0.0.1:18081/apis/ccompat/v7}") String registryUrl) {
        if (!registryUrl.startsWith("http://") && !registryUrl.startsWith("https://"))
            throw new IllegalArgumentException("Invalid Registry URL");
        var properties = new HashMap<>(existing.getConfigurationProperties());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        properties.put("schema.registry.url", registryUrl);
        properties.put("auto.register.schemas", false);
        properties.put("use.latest.version", true);
        properties.put("latest.compatibility.strict", true);
        properties.put("avro.remove.java.properties", true);
        properties.put("value.subject.name.strategy", "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy");
        factory = new DefaultKafkaProducerFactory<>(properties);
        template = new KafkaTemplate<>(factory);
    }

    public CompletableFuture<SendResult<String,Object>> send(String topic, String key,
            ReadingProgressChangedEvent event) {
        return template.send(topic,key,ReadingProgressAvroMapper.toAvro(event));
    }
    @Override @PreDestroy public void close() { factory.destroy(); }
}
