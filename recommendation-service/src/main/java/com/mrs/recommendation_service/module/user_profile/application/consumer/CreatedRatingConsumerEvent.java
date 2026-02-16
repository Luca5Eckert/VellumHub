package com.mrs.recommendation_service.module.user_profile.application.consumer;

import com.mrs.recommendation_service.module.user_profile.application.event.CreatedRatingEvent;
import com.mrs.recommendation_service.module.user_profile.application.handler.CreatedRatingConsumerHandler;
import com.mrs.recommendation_service.module.user_profile.domain.use_case.UpdateUserProfileWithRatingUseCase;
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
            @Payload CreatedRatingEvent createdRatingEvent
    ) {
        log.info("Evento recebido:  User={}, Media={}, Stars={}",
                createdRatingEvent.userId(),
                createdRatingEvent.bookId(),
                createdRatingEvent.stars());

        createdRatingConsumerHandler.handle(createdRatingEvent);

    }

}