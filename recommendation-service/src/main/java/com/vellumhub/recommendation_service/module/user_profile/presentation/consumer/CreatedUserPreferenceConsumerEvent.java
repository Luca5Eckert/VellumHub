package com.vellumhub.recommendation_service.module.user_profile.presentation.consumer;

import com.vellumhub.recommendation_service.module.user_profile.application.command.CreatedUserProfileCommand;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.CreateUserProfileUseCase;
import com.vellumhub.recommendation_service.module.user_profile.presentation.event.CreatedUserPreferenceEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CreatedUserPreferenceConsumerEvent {

    private final CreateUserProfileUseCase createUserProfileUseCase;

    public CreatedUserPreferenceConsumerEvent(CreateUserProfileUseCase createUserProfileUseCase) {
        this.createUserProfileUseCase = createUserProfileUseCase;
    }

    @KafkaListener(
            topics = "create_user_preference",
            groupId = "recommendation_service_group"
    )
    public void consume(@Payload CreatedUserPreferenceEvent event) {
        log.info("Consumed CreatedUserPreferenceEvent: {}", event);

        try {
            var command = CreatedUserProfileCommand.of(
                    event.userId(),
                    event.genres(),
                    event.about()
            );
            createUserProfileUseCase.execute(command);

            log.info("Successfully processed CreatedUserPreferenceEvent for userId: {}", event.userId());

        } catch (Exception e) {
            log.error("Error processing CreatedUserPreferenceEvent: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao processar CreatedUserPreferenceEvent", e);
        }
    }


}
