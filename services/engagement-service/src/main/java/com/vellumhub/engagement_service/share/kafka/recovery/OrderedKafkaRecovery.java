package com.vellumhub.engagement_service.share.kafka.recovery;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.listener.adapter.RecordFilterStrategy;
import org.springframework.kafka.listener.adapter.RecordMessagingMessageListenerAdapter;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Ordered streams must never skip an unapplied revision. Exhaustion publishes a
 * diagnostic DLT copy, persists a partition block, and leaves the source offset
 * uncommitted. Operators request replay of the ORIGINAL offset and restart the
 * consumer service; DLT records are not reinserted at the tail of ordered topics.
 */
@Slf4j
@Component
public class OrderedKafkaRecovery {
    private static final String SOURCE_TOPIC = "x-vellum-source-topic";
    private static final String SOURCE_PARTITION = "x-vellum-source-partition";
    private static final String SOURCE_OFFSET = "x-vellum-source-offset";
    private static final String SOURCE_GROUP = "x-vellum-consumer-group";
    private static final String CATEGORY = "x-vellum-failure-category";
    private static final String EVENT_ID = "x-vellum-event-id";
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, byte[]> publisher;
    private final KafkaListenerEndpointRegistry registry;
    private final MeterRegistry metrics;
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final String service;
    private final long retryBackoffMs;
    private final long maxRetries;

    public OrderedKafkaRecovery(JdbcTemplate jdbc,
            @Qualifier("orderedRecoveryDltKafkaTemplate") KafkaTemplate<String, byte[]> publisher,
            KafkaListenerEndpointRegistry registry, MeterRegistry metrics,
            @Value("${spring.application.name}") String service,
            @Value("${app.kafka.recovery.backoff-ms:1000}") long retryBackoffMs,
            @Value("${app.kafka.recovery.max-retries:3}") long maxRetries) {
        if (retryBackoffMs < 1 || maxRetries < 0 || maxRetries > 20) {
            throw new IllegalArgumentException("Invalid ordered Kafka recovery retry policy");
        }
        this.jdbc = jdbc;
        this.publisher = publisher;
        this.registry = registry;
        this.metrics = metrics;
        this.service = service;
        this.retryBackoffMs = retryBackoffMs;
        this.maxRetries = maxRetries;
    }

    public void configure(ConcurrentKafkaListenerContainerFactory<String, Object> factory,
            String consumerGroup) {
        var handler = new DefaultErrorHandler(
                (record, error) -> quarantine(record, error, consumerGroup),
                new FixedBackOff(retryBackoffMs, maxRetries));
        // Business precondition failures are still bounded; malformed contracts do
        // not consume the entire retry budget.
        handler.addNotRetryableExceptions(IllegalArgumentException.class,
                DataIntegrityViolationException.class, DeserializationException.class);
        handler.setAckAfterHandle(false);
        handler.setRetryListeners((record, error, attempt) -> {
            if (attempt == 1) {
                log.warn("operation=kafka_retry topic={} partition={} offset={} consumer_group={} cause={}",
                        record.topic(), record.partition(), record.offset(), consumerGroup,
                        classify(error));
            }
            metrics.counter("vellumhub.kafka.recovery.retry",
                    "service", service, "topic", record.topic(),
                    "consumer_group", consumerGroup).increment();
        });
        factory.setCommonErrorHandler(handler);
        factory.getContainerProperties().setConsumerRebalanceListener(new ConsumerAwareRebalanceListener() {
            @Override
            public void onPartitionsAssigned(Consumer<?, ?> consumer,
                    Collection<TopicPartition> partitions) {
                for (var tp : partitions) {
                    var rows = jdbc.query("""
                            SELECT source_offset, status FROM kafka_consumer_quarantine
                            WHERE consumer_group=? AND source_topic=? AND source_partition=?
                              AND status <> 'RESOLVED' ORDER BY source_offset LIMIT 1
                            """, (rs, n) -> new Block(rs.getLong(1), rs.getString(2)),
                            consumerGroup, tp.topic(), tp.partition());
                    if (rows.isEmpty()) continue;
                    var block = rows.getFirst();
                    if ("BLOCKED".equals(block.status())) {
                        consumer.pause(List.of(tp));
                        log.warn("operation=kafka_partition_blocked topic={} partition={} offset={} group={}",
                                tp.topic(), tp.partition(), block.offset(), consumerGroup);
                    } else if ("RETRY_REQUESTED".equals(block.status())) {
                        consumer.seek(tp, block.offset());
                        log.info("operation=kafka_replay_seek topic={} partition={} offset={} group={}",
                                tp.topic(), tp.partition(), block.offset(), consumerGroup);
                    }
                }
            }
        });
        factory.setRecordInterceptor(new RecordInterceptor<>() {
            @Override
            public ConsumerRecord<String, Object> intercept(ConsumerRecord<String, Object> record,
                    Consumer<String, Object> consumer) {
                return record;
            }
            @Override
            public void success(ConsumerRecord<String, Object> record,
                    Consumer<String, Object> consumer) {
                int updated = jdbc.update("""
                        UPDATE kafka_consumer_quarantine SET status='RESOLVED', updated_at=NOW()
                        WHERE consumer_group=? AND source_topic=? AND source_partition=?
                          AND source_offset=? AND status='RETRY_REQUESTED'
                        """, consumerGroup, record.topic(), record.partition(), record.offset());
                if (updated > 0) {
                    metrics.counter("vellumhub.kafka.recovery.resolved",
                            "service", service, "topic", record.topic(),
                            "consumer_group", consumerGroup).increment();
                    log.info("operation=kafka_replay_resolved topic={} partition={} offset={} group={}",
                            record.topic(), record.partition(), record.offset(), consumerGroup);
                }
            }
        });
    }

    private void quarantine(ConsumerRecord<?, ?> record, Exception error, String group) {
        String dltTopic = record.topic() + "." + service + "-dlt";
        String category = classify(error);
        UUID eventId = eventId(record.value());
        try {
            // DLT publication is acknowledged BEFORE recording a recoverable block.
            // The original Kafka offset is not committed; duplicate DLT copies after
            // a crash are possible and are distinguished by source coordinates.
            var headers = new RecordHeaders();
            Header typeId = record.headers().lastHeader("__TypeId__");
            if (typeId != null) headers.add("__TypeId__", typeId.value());
            add(headers, SOURCE_TOPIC, record.topic());
            add(headers, SOURCE_PARTITION, Integer.toString(record.partition()));
            add(headers, SOURCE_OFFSET, Long.toString(record.offset()));
            add(headers, SOURCE_GROUP, group);
            add(headers, CATEGORY, category);
            if (eventId != null) add(headers, EVENT_ID, eventId.toString());
            var correlation = record.headers().lastHeader("x-correlation-id");
            if (correlation != null && correlation.value() != null
                    && correlation.value().length <= 128) {
                headers.add("x-correlation-id", correlation.value());
            }
            byte[] payload = record.value() instanceof byte[] bytes
                    ? bytes : mapper.writeValueAsBytes(record.value());
            publisher.send(new ProducerRecord<>(dltTopic, null,
                    String.valueOf(record.key()), payload, headers)).get(10, TimeUnit.SECONDS);
            jdbc.update("""
                    INSERT INTO kafka_consumer_quarantine
                    (consumer_group,source_topic,source_partition,source_offset,
                     event_id,failure_category,attempts,status,dlt_topic)
                    VALUES(?,?,?,?,?,?,?,'BLOCKED',?)
                    ON CONFLICT (consumer_group,source_topic,source_partition,source_offset)
                    DO UPDATE SET status='BLOCKED', attempts=EXCLUDED.attempts,
                                  failure_category=EXCLUDED.failure_category,
                                  updated_at=NOW()
                    """, group, record.topic(), record.partition(), record.offset(),
                    eventId, category, Math.toIntExact(maxRetries + 1), dltTopic);
            TopicPartition tp = new TopicPartition(record.topic(), record.partition());
            for (MessageListenerContainer container : registry.getListenerContainers()) {
                var assigned = container.getAssignedPartitions();
                if (assigned != null && assigned.contains(tp)) {
                    container.pausePartition(tp);
                }
            }
            metrics.counter("vellumhub.kafka.recovery.quarantined",
                    "service", service, "topic", record.topic(),
                    "consumer_group", group, "category", category).increment();
            log.error("operation=kafka_quarantined topic={} partition={} offset={} group={} category={} dlt={}",
                    record.topic(), record.partition(), record.offset(), group, category, dltTopic);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted before Kafka quarantine completed", interrupted);
        } catch (Exception publishOrStoreFailure) {
            // Do NOT recover / acknowledge the source record if publication or
            // durable quarantine failed. Retry at the Kafka transport boundary.
            throw new IllegalStateException("Unable to persist Kafka quarantine", publishOrStoreFailure);
        }
    }

    private UUID eventId(Object value) {
        if (value == null) return null;
        try {
            var id = mapper.valueToTree(value).path("eventId").asText("");
            return id.isBlank() ? null : UUID.fromString(id);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void add(RecordHeaders headers, String name, String value) {
        headers.add(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private static String classify(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof IllegalArgumentException
                    || cause instanceof DeserializationException
                    || cause instanceof DataIntegrityViolationException) {
                return "INVALID";
            }
        }
        return "RETRY_EXHAUSTED";
    }

    private record Block(long offset, String status) {}
}
