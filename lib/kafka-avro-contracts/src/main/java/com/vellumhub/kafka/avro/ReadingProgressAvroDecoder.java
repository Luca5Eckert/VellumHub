package com.vellumhub.kafka.avro;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import java.util.Map;
import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;

/** Decode from raw Kafka bytes so ordered DLT replay retains the original schema ID. */
public final class ReadingProgressAvroDecoder implements AutoCloseable {
    private final KafkaAvroDeserializer reader = new KafkaAvroDeserializer();

    public ReadingProgressAvroDecoder(String registryUrl) {
        if (!registryUrl.startsWith("http://") && !registryUrl.startsWith("https://"))
            throw new IllegalArgumentException("Invalid Registry URL");
        reader.configure(Map.of("schema.registry.url", registryUrl, "specific.avro.reader", true), false);
    }

    public ReadingProgressChangedEvent decode(String topic, String key, byte[] bytes) {
        if (bytes == null || bytes.length < 6 || bytes[0] != 0)
            throw new IllegalArgumentException("Invalid Confluent Avro frame");
        Object value = reader.deserialize(topic, bytes);
        if (!(value instanceof com.vellumhub.kafka.avro.readingprogress.ReadingProgressChangedEvent fact))
            throw new IllegalArgumentException("Unexpected Reading Progress schema");
        return ReadingProgressAvroMapper.fromAvro(key, fact);
    }

    @Override public void close() { reader.close(); }
}
