package com.vellumhub.testing.distributed.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.StreamSupport;

public final class KafkaProbe implements AutoCloseable {

    private final KafkaConsumer<String, byte[]> consumer;

    private KafkaProbe(KafkaConsumer<String, byte[]> consumer) {
        this.consumer = consumer;
    }

    public static KafkaProbe subscribe(String bootstrapServers, String topic, String groupPrefix) {
        String uniqueSuffix = UUID.randomUUID().toString();
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupPrefix + "-" + uniqueSuffix);
        properties.put(ConsumerConfig.CLIENT_ID_CONFIG, groupPrefix + "-probe-" + uniqueSuffix);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());

        KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(List.of(topic));
        return new KafkaProbe(consumer);
    }

    public ConsumerRecord<String, byte[]> awaitRecord(
            String expectedTopic,
            String expectedKey,
            Duration timeout,
            Duration pollInterval
    ) {
        Instant deadline = Instant.now().plus(timeout);

        while (Instant.now().isBefore(deadline)) {
            ConsumerRecords<String, byte[]> records = consumer.poll(pollInterval);
            for (ConsumerRecord<String, byte[]> record : records) {
                if (expectedTopic.equals(record.topic()) && expectedKey.equals(record.key())) {
                    return record;
                }
            }
        }

        throw new AssertionError(
                "Kafka record not observed within " + timeout + " for topic=" + expectedTopic + ", key=" + expectedKey
        );
    }

    public static List<String> utf8HeaderValues(ConsumerRecord<?, ?> record, String headerName) {
        return StreamSupport.stream(record.headers().headers(headerName).spliterator(), false)
                .map(header -> new String(header.value(), StandardCharsets.UTF_8))
                .toList();
    }

    @Override
    public void close() {
        consumer.close(Duration.ofSeconds(5));
    }
}
