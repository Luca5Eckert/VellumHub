package com.mrs.recommendation_service.application.consumer;

import com.mrs.recommendation_service.application.event.CreatedRatingEvent;
import com.mrs.recommendation_service.domain.command.CreateRatingCommand;
import com.mrs.recommendation_service.domain.handler.rating.UpdateUserProfileWithRatingHandler;
import com.mrs.recommendation_service.domain.handler.user_profile.UpdateUserProfileHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CreatedRatingConsumerEvent {

    private final UpdateUserProfileWithRatingHandler updateUserProfileWithRatingHandler;

    public CreatedRatingConsumerEvent(UpdateUserProfileWithRatingHandler updateUserProfileWithRatingHandler) {
        this.updateUserProfileWithRatingHandler = updateUserProfileWithRatingHandler;
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

        CreateRatingCommand updateUserProfileCommand = new CreateRatingCommand(
                createdRatingEvent.userId(),
                createdRatingEvent.bookId(),
                createdRatingEvent.stars()
        );

    }

}