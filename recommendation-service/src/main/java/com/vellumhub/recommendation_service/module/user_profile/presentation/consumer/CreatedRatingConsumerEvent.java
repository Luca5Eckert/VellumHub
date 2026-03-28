package com.vellumhub.recommendation_service.module.user_profile.presentation.consumer;

import com.vellumhub.recommendation_service.module.user_profile.application.command.UpdateUserProfileWithRatingCommand;
import com.vellumhub.recommendation_service.module.user_profile.presentation.event.CreatedRatingEvent;
import com.vellumhub.recommendation_service.module.user_profile.application.handler.CreatedRatingConsumerHandler;
import com.vellumhub.recommendation_service.module.user_profile.domain.use_case.UpdateUserProfileWithRatingUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CreatedRatingConsumerEvent {

    private final CreatedRatingConsumerHandler createdRatingConsumerHandler;

    public CreatedRatingConsumerEvent(CreatedRatingConsumerHandler createdRatingConsumerHandler) {
        this.createdRatingConsumerHandler = createdRatingConsumerHandler;
    }

    @KafkaListener(
            topics = "created-rating",
            groupId = "recommendation-service"
    )
    public void consume(
            @Payload CreatedRatingEvent event
    ) {
        log.info("Event received: Rating created. UserId={}, BookId={}, Stars={}",
                event.userId(),
                event.bookId(),
                event.stars());

        try {
            var command = new UpdateUserProfileWithRatingCommand(
                    event.userId(),
                    event.bookId(),
                    0,
                    event.stars(),
                    false
            );

            createdRatingConsumerHandler.handle(command);
            

            log.info("Rating event processed successfully. UserId={}, BookId={}",
                    event.userId(),
                    event.bookId());

        } catch (Exception e) {
            log.error("Error processing rating event. UserId={}, BookId={}, Stars={}",
                    event.userId(),
                    event.bookId(),
                    event.stars(),
                    e);
            throw e;
        }

    }

}