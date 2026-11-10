package com.vellumhub.catalog_service.module.outbox.infrastructure.kafka;

import com.vellumhub.kafka.avro.BookAvroMapper;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
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

/** Dedicated serializer, inheriting broker security and ACK settings from JSON factory. */
@Component
public class BookAvroPublisher implements AutoCloseable {
    private final DefaultKafkaProducerFactory<String, Object> factory;
    private final KafkaTemplate<String, Object> template;

    public BookAvroPublisher(ProducerFactory<String, Object> currentProducer,
            @Value("${app.book.avro.registry-url:http://127.0.0.1:18081/apis/ccompat/v7}") String registryUrl) {
        if (!registryUrl.startsWith("http://") && !registryUrl.startsWith("https://"))
            throw new IllegalArgumentException("Invalid Schema Registry URL");
        var options = new HashMap<>(currentProducer.getConfigurationProperties());
        options.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        options.put("schema.registry.url", registryUrl);
        options.put("auto.register.schemas", false);
        // Use the pre-approved Registry writer schema instead of a brittle
        // byte-for-byte lookup against the code-generated SpecificRecord schema.
        // Never auto-register; reject incompatible Registry revisions.
        options.put("use.latest.version", true);
        options.put("latest.compatibility.strict", true);
        options.put("avro.remove.java.properties", true);
        options.put("value.subject.name.strategy",
                "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy");
        this.factory = new DefaultKafkaProducerFactory<>(options);
        this.template = new KafkaTemplate<>(factory);
    }
    public CompletableFuture<SendResult<String, Object>> send(
            String topic, String key, BookChangedEvent event) {
        return template.send(topic, key, BookAvroMapper.toAvro(event));
    }
    @Override @PreDestroy public void close() { factory.destroy(); }
}
