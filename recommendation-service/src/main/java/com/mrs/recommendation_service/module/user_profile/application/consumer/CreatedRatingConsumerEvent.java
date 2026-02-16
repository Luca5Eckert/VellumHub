package com.mrs.recommendation_service.module.user_profile.application.consumer;

import com.mrs.recommendation_service.module.user_profile.application.event.CreatedRatingEvent;
import com.mrs.recommendation_service.module.user_profile.domain.command.CreateRatingCommand;
import com.mrs.recommendation_service.module.user_profile.domain.use_case.UpdateUserProfileWithRatingUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CreatedRatingConsumerEvent {

    private final UpdateUserProfileWithRatingUseCase updateUserProfileWithRatingUseCase;

    public CreatedRatingConsumerEvent(UpdateUserProfileWithRatingUseCase updateUserProfileWithRatingUseCase) {
        this.updateUserProfileWithRatingUseCase = updateUserProfileWithRatingUseCase;
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