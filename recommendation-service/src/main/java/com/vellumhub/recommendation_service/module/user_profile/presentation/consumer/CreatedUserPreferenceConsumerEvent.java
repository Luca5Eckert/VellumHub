package com.vellumhub.recommendation_service.module.user_profile.presentation.consumer;

import com.vellumhub.recommendation_service.module.user_profile.application.command.CreatedUserProfileCommand;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.CreatUserProfileUseCase;
import com.vellumhub.recommendation_service.module.user_profile.presentation.event.CreatedUserPreferenceEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CreatedUserPreferenceConsumerEvent {

    private final CreatUserProfileUseCase creatUserProfileUseCase;

    public CreatedUserPreferenceConsumerEvent(CreatUserProfileUseCase creatUserProfileUseCase) {
        this.creatUserProfileUseCase = creatUserProfileUseCase;
    }

    @KafkaListener(
            topics = "create_user_preference",
            groupId = "recommendation_service_group"
    )
    public void consume(CreatedUserPreferenceEvent event) {
        log.info("Consumed CreatedUserPreferenceEvent: {}", event);

        try {
            var command = CreatedUserProfileCommand.of(
                    event.userId(),
                    event.genres(),
                    event.about()
            );
            creatUserProfileUseCase.execute(command);

            log.info("Successfully processed CreatedUserPreferenceEvent for userId: {}", event.userId());

        } catch (Exception e) {
            log.error("Error processing CreatedUserPreferenceEvent: {}", e.getMessage(), e);
        }
    }


}
