package com.vellumhub.kafka.avro;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import java.util.Map;

/** Keep original bytes at the listener/recovery boundary for DLT and offset replay. */
public final class EngagementAvroDecoder implements AutoCloseable {
    private final KafkaAvroDeserializer reader = new KafkaAvroDeserializer();

    public EngagementAvroDecoder(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://"))
            throw new IllegalArgumentException("Invalid Registry URL");
        reader.configure(Map.of("schema.registry.url",url,"specific.avro.reader",true),false);
    }

    public Object decode(String topic,String key,byte[] bytes) {
        if (bytes == null || bytes.length < 6 || bytes[0] != 0)
            throw new IllegalArgumentException("Invalid Confluent Avro wire");
        return EngagementAvroMapper.fromAvro(key, reader.deserialize(topic,bytes));
    }

    @Override public void close() { reader.close(); }
}
