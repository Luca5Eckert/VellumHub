package com.vellumhub.engagement_service.module.outbox.infrastructure.kafka;

import com.vellumhub.kafka.avro.EngagementAvroMapper;
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

/** One serializer for six SpecificRecords with distinct TopicRecordNameStrategy subjects. */
@Component
public class EngagementAvroPublisher implements AutoCloseable {
    private final DefaultKafkaProducerFactory<String,Object> factory;
    private final KafkaTemplate<String,Object> template;

    public EngagementAvroPublisher(ProducerFactory<String,Object> existing,
            @Value("${app.engagement.avro.registry-url:http://127.0.0.1:18081/apis/ccompat/v7}") String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://"))
            throw new IllegalArgumentException("Invalid Registry URL");
        var config=new HashMap<>(existing.getConfigurationProperties());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,KafkaAvroSerializer.class);
        config.put("schema.registry.url",url);
        config.put("auto.register.schemas",false);
        config.put("use.latest.version",true);
        config.put("latest.compatibility.strict",true);
        config.put("avro.remove.java.properties",true);
        config.put("value.subject.name.strategy","io.confluent.kafka.serializers.subject.TopicRecordNameStrategy");
        factory=new DefaultKafkaProducerFactory<>(config);
        template=new KafkaTemplate<>(factory);
    }

    public CompletableFuture<SendResult<String,Object>> send(String topic,String key,Object fact) {
        return template.send(topic,key,EngagementAvroMapper.toAvro(fact));
    }

    @Override @PreDestroy public void close() { factory.destroy(); }
}
