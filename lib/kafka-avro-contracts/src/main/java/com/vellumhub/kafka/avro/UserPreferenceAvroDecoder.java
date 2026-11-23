package com.vellumhub.kafka.avro;

import com.vellumhub.kafka.avro.userpreference.UserPreferenceChangedEvent;
import com.vellumhub.kafka.contracts.user.UserPreferenceChanged;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;

import java.util.Map;

public final class UserPreferenceAvroDecoder implements AutoCloseable {
    private final KafkaAvroDeserializer reader = new KafkaAvroDeserializer();

    public UserPreferenceAvroDecoder(String registryUrl) {
        if (!registryUrl.startsWith("http://") && !registryUrl.startsWith("https://")) {
            throw new IllegalArgumentException("Invalid Schema Registry URL");
        }
        reader.configure(Map.of("schema.registry.url", registryUrl, "specific.avro.reader", true), false);
    }

    public UserPreferenceChanged decode(String topic, String key, byte[] bytes) {
        if (bytes == null || bytes.length < 6 || bytes[0] != 0) {
            throw new IllegalArgumentException("Invalid Confluent Avro wire header");
        }
        Object value = reader.deserialize(topic, bytes);
        if (!(value instanceof UserPreferenceChangedEvent event)) {
            throw new IllegalArgumentException("Unexpected Avro preference record");
        }
        return UserPreferenceAvroMapper.fromAvro(key, event);
    }

    @Override
    public void close() {
        reader.close();
    }
}
