package com.vellumhub.catalog_service.module.book.infrastructure.producer;

import com.vellumhub.catalog_service.share.metrics.VellumHubMetrics;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.CreateBookEvent;
import com.vellumhub.testing.distributed.container.KafkaIntegrationTestSupport;
import com.vellumhub.testing.distributed.fixture.BookEventFixtures;
import com.vellumhub.testing.distributed.kafka.KafkaProbe;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("distributed")
class KafkaBookEventProducerDistributedTest extends KafkaIntegrationTestSupport {

    private DefaultKafkaProducerFactory<String, Object> producerFactory;
    private KafkaBookEventProducer<String, Object> producer;

    @BeforeEach
    void setUp() {
        producerFactory = new DefaultKafkaProducerFactory<>(producerProperties());
        KafkaTemplate<String, Object> kafkaTemplate = new KafkaTemplate<>(producerFactory);
        producer = new KafkaBookEventProducer<>(
                kafkaTemplate,
                new VellumHubMetrics(new SimpleMeterRegistry())
        );
    }

    @AfterEach
    void tearDown() {
        producerFactory.destroy();
    }

    @Test
    void publishesCreateBookContractWithProductionProducerIntoRealKafka() {
        UUID bookId = UUID.randomUUID();
        CreateBookEvent event = BookEventFixtures.createdBook(bookId);

        try (KafkaProbe probe = KafkaProbe.subscribe(
                KAFKA.getBootstrapServers(),
                KafkaTopics.CREATED_BOOK,
                "catalog-created-book"
        )) {
            producer.send(KafkaTopics.CREATED_BOOK, bookId.toString(), event);

            ConsumerRecord<String, byte[]> record = probe.awaitRecord(
                    KafkaTopics.CREATED_BOOK,
                    bookId.toString(),
                    ASYNC_TIMEOUT,
                    ASYNC_POLL_INTERVAL
            );

            assertThat(record.topic()).isEqualTo(KafkaTopics.CREATED_BOOK);
            assertThat(record.key()).isEqualTo(bookId.toString());
            assertThat(new String(record.value(), StandardCharsets.UTF_8))
                    .contains(bookId.toString())
                    .contains(event.title());
            assertThat(KafkaProbe.utf8HeaderValues(record, "__TypeId__"))
                    .contains("create_book_event");
        }
    }

    private Map<String, Object> producerProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        properties.put("spring.json.add.type.headers", true);
        properties.put(
                "spring.json.type.mapping",
                "create_book_event:com.vellumhub.kafka.contracts.book.CreateBookEvent"
        );
        return properties;
    }
}
