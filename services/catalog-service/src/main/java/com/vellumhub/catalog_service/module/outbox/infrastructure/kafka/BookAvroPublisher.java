package com.vellumhub.catalog_service.module.outbox.infrastructure.kafka;

import com.vellumhub.kafka.avro.BookAvroMapper;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    public BookAvroPublisher(ProducerFactory<String, Object> currentProducer,
            @Value("${app.book.avro.registry-url:}") String registryUrl,
            @Value("${app.book.events.wire-format:json}") String wireFormat) {
        if (!"json".equalsIgnoreCase(wireFormat) && !"avro".equalsIgnoreCase(wireFormat))
            throw new IllegalArgumentException("Invalid Book event wire format");
        if ("avro".equalsIgnoreCase(wireFormat) && (registryUrl == null || registryUrl.isBlank()))
            throw new IllegalArgumentException("Book Avro requires an explicit Schema Registry URL");
        if (registryUrl != null && !registryUrl.isBlank()) {
            java.net.URI uri;
            try { uri = java.net.URI.create(registryUrl); }
            catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Invalid Schema Registry URL", ex); }
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null)
                throw new IllegalArgumentException("Invalid Schema Registry URL");
        }
        var options = new HashMap<>(currentProducer.getConfigurationProperties());
        options.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        options.put("schema.registry.url", registryUrl == null ? "" : registryUrl);
        options.put("auto.register.schemas", false);
        // Resolve the generated writer against its Git-approved registered schema.
        // Do not silently adopt a newer subject revision during a rolling deployment.
        options.put("use.latest.version", false);
        options.put("avro.remove.java.properties", true);
        options.put("value.subject.name.strategy",
                "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy");
        this.factory = new DefaultKafkaProducerFactory<>(options);
        this.template = new KafkaTemplate<>(factory);
    }
    /** Explicit-URL constructor for tests and opt-in integrations. */
    public BookAvroPublisher(ProducerFactory<String, Object> currentProducer, String registryUrl) {
        this(currentProducer, registryUrl, "avro");
    }

    public CompletableFuture<SendResult<String, Object>> send(
            String topic, String key, BookChangedEvent event) {
        return template.send(topic, key, BookAvroMapper.toAvro(event));
    }
    @Override @PreDestroy public void close() { factory.destroy(); }
}
