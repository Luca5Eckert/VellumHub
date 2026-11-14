package com.vellumhub.recommendation_service.module.user_profile.infrastructure.consumer;

import com.vellumhub.kafka.avro.EngagementAvroDecoder;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.recommendation_service.share.kafka.recovery.OrderedKafkaRecovery;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import java.util.HashMap;

@Configuration(proxyBeanMethods=false)
public class OrderedEngagementAvroKafkaConfig {
    @Bean
    EngagementAvroDecoder engagementAvroDecoder(@Value("${app.engagement.avro.registry-url:http://127.0.0.1:18081/apis/ccompat/v7}") String url) {
        return new EngagementAvroDecoder(url);
    }

    @Bean("orderedRatingAvroContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String,Object> ratingAvro(
            ConsumerFactory<String,Object> legacyFactory, OrderedKafkaRecovery recovery) {
        return configure(legacyFactory,recovery,KafkaConsumerGroups.RECOMMENDATION_RATINGS_AVRO);
    }

    @Bean("orderedReactionAvroContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String,Object> reactionAvro(
            ConsumerFactory<String,Object> legacyFactory, OrderedKafkaRecovery recovery) {
        return configure(legacyFactory,recovery,KafkaConsumerGroups.RECOMMENDATION_REACTIONS_AVRO);
    }

    private ConcurrentKafkaListenerContainerFactory<String,Object> configure(
            ConsumerFactory<String,Object> legacyFactory, OrderedKafkaRecovery recovery,String group) {
        var config=new HashMap<>(legacyFactory.getConfigurationProperties());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,ByteArrayDeserializer.class);
        config.put(ConsumerConfig.GROUP_ID_CONFIG,group);
        var factory=new ConcurrentKafkaListenerContainerFactory<String,Object>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(config));
        factory.setConcurrency(1);
        recovery.configure(factory,group);
        return factory;
    }
}
