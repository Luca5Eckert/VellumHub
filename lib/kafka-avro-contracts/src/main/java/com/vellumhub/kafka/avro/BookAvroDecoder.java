package com.vellumhub.kafka.avro;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import java.util.Map;

/** Source Avro bytes are retained by the listener for exact DLT replay. */
public final class BookAvroDecoder implements AutoCloseable {
    private final KafkaAvroDeserializer reader = new KafkaAvroDeserializer();
    public BookAvroDecoder(String registryUrl) {
        if (!registryUrl.startsWith("http://") && !registryUrl.startsWith("https://"))
            throw new IllegalArgumentException("Invalid Schema Registry URL");
        reader.configure(Map.of("schema.registry.url", registryUrl,
                "specific.avro.reader", true), false);
    }
    public com.vellumhub.kafka.contracts.book.BookChangedEvent decode(
            String topic, String key, byte[] bytes) {
        if (bytes == null || bytes.length < 6 || bytes[0] != 0)
            throw new IllegalArgumentException("Invalid Confluent Avro wire header");
        Object value = reader.deserialize(topic, bytes);
        if (!(value instanceof com.vellumhub.kafka.avro.book.BookChangedEvent event))
            throw new IllegalArgumentException("Unexpected Avro Book record");
        return BookAvroMapper.fromAvro(key, event);
    }
    @Override public void close() { reader.close(); }
}
