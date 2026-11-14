package com.vellumhub.recommendation_service.module.user_profile.infrastructure.consumer;

import com.vellumhub.kafka.avro.EngagementAvroDecoder;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.KafkaTopics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** One application pipeline, distinct wire listeners: no duplicate UserProfile projection. */
@Component
public class OrderedEngagementAvroConsumer {
    private final EngagementAvroDecoder decoder;
    private final EngagementEventProcessor processor;

    public OrderedEngagementAvroConsumer(EngagementAvroDecoder decoder,
            EngagementEventProcessor processor) {
        this.decoder=decoder; this.processor=processor;
    }

    @KafkaListener(topics=KafkaTopics.ENGAGEMENT_RATINGS_AVRO,
            groupId=KafkaConsumerGroups.RECOMMENDATION_RATINGS_AVRO,
            containerFactory="orderedRatingAvroContainerFactory",
            autoStartup="${app.engagement.ratings.avro.consumer-enabled:false}")
    public void ratings(ConsumerRecord<String,byte[]> record) {
        processor.apply(decoder.decode(record.topic(),record.key(),record.value()));
    }

    @KafkaListener(topics=KafkaTopics.ENGAGEMENT_REACTIONS_AVRO,
            groupId=KafkaConsumerGroups.RECOMMENDATION_REACTIONS_AVRO,
            containerFactory="orderedReactionAvroContainerFactory",
            autoStartup="${app.engagement.reactions.avro.consumer-enabled:false}")
    public void reactions(ConsumerRecord<String,byte[]> record) {
        processor.apply(decoder.decode(record.topic(),record.key(),record.value()));
    }
}
