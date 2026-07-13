package com.vellumhub.recommendation_service.module.user_profile.presentation.consumer;

import com.vellumhub.recommendation_service.module.user_profile.application.command.UpdateUserProfileWithRatingCommand;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.UpdateUserProfileWithRatingUseCase;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.CreatedRatingEvent;
import com.vellumhub.recommendation_service.share.metrics.VellumHubMetrics;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CreatedRatingConsumerEvent {

    private static final String TOPIC = KafkaTopics.CREATED_RATING;
    private static final String EVENT_TYPE = "CreatedRatingEvent";
    private static final String CONSUMER_GROUP = KafkaConsumerGroups.RECOMMENDATION_SERVICE;

    private final UpdateUserProfileWithRatingUseCase updateUserProfileWithRatingUseCase;
    private final VellumHubMetrics metrics;

    public CreatedRatingConsumerEvent(UpdateUserProfileWithRatingUseCase updateUserProfileWithRatingUseCase, VellumHubMetrics metrics) {
        this.updateUserProfileWithRatingUseCase = updateUserProfileWithRatingUseCase;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = KafkaTopics.CREATED_RATING,
            groupId = KafkaConsumerGroups.RECOMMENDATION_SERVICE
    )
    public void consume(
            @Payload CreatedRatingEvent event
    ) {
        Timer.Sample sample = metrics.startKafkaProcessing();
        log.info("Event received: Rating created. UserId={}, BookId={}, Stars={}",
                event.userId(),
                event.bookId(),
                event.stars());

        var command = new UpdateUserProfileWithRatingCommand(
                event.userId(),
                event.bookId(),
                0,
                event.stars(),
                false
        );

        try {
            updateUserProfileWithRatingUseCase.execute(command);
            metrics.recordKafkaConsumed(TOPIC, EVENT_TYPE, CONSUMER_GROUP);
            metrics.recordKafkaProcessingDuration(sample, TOPIC, EVENT_TYPE, CONSUMER_GROUP, "success");
        } catch (RuntimeException ex) {
            metrics.recordKafkaConsumeFailed(TOPIC, EVENT_TYPE, CONSUMER_GROUP);
            metrics.recordKafkaProcessingDuration(sample, TOPIC, EVENT_TYPE, CONSUMER_GROUP, "failure");
            throw ex;
        }


        log.info("Rating event processed successfully. UserId={}, BookId={}",
                event.userId(),
                event.bookId()
        );
    }

}
