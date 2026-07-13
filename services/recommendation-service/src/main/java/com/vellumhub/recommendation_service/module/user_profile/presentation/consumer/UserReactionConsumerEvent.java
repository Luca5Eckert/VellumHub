package com.vellumhub.recommendation_service.module.user_profile.presentation.consumer;

import com.vellumhub.recommendation_service.module.user_profile.application.command.ReactionChangedCommand;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.ReactionChangedUseCase;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.ReactionChangedEvent;
import com.vellumhub.recommendation_service.share.metrics.VellumHubMetrics;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UserReactionConsumerEvent {

    private static final String TOPIC = KafkaTopics.USER_REACTION_CHANGED;
    private static final String EVENT_TYPE = "ReactionChangedEvent";
    private static final String CONSUMER_GROUP = KafkaConsumerGroups.RECOMMENDATION_SERVICE;

    private final ReactionChangedUseCase reactionChangedUseCase;
    private final VellumHubMetrics metrics;

    public UserReactionConsumerEvent(ReactionChangedUseCase reactionChangedUseCase, VellumHubMetrics metrics) {
        this.reactionChangedUseCase = reactionChangedUseCase;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = KafkaTopics.USER_REACTION_CHANGED,
            groupId = KafkaConsumerGroups.RECOMMENDATION_SERVICE
    )
    public void consume(ReactionChangedEvent event) {
        Timer.Sample sample = metrics.startKafkaProcessing();
        log.info("Event received: User reaction changed. UserId={}, BookId={}, Reaction={}",
                event.userId(),
                event.bookId(),
                event.typeReaction());

        var command = ReactionChangedCommand.of(
                event.userId(),
                event.bookId(),
                event.typeReaction()
        );

        try {
            reactionChangedUseCase.execute(command);
            metrics.recordKafkaConsumed(TOPIC, EVENT_TYPE, CONSUMER_GROUP);
            metrics.recordKafkaProcessingDuration(sample, TOPIC, EVENT_TYPE, CONSUMER_GROUP, "success");
        } catch (RuntimeException ex) {
            metrics.recordKafkaConsumeFailed(TOPIC, EVENT_TYPE, CONSUMER_GROUP);
            metrics.recordKafkaProcessingDuration(sample, TOPIC, EVENT_TYPE, CONSUMER_GROUP, "failure");
            throw ex;
        }

        log.info("User reaction change event processed successfully. UserId={}, BookId={}",
                event.userId(),
                event.bookId());
    }

}
