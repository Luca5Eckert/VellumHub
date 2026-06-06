package com.vellumhub.recommendation_service.module.user_profile.presentation.consumer;

import com.vellumhub.recommendation_service.module.user_profile.application.command.CreatedUserProfileCommand;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.CreateUserProfileUseCase;
import com.vellumhub.recommendation_service.module.user_profile.presentation.event.CreatedUserPreferenceEvent;
import com.vellumhub.recommendation_service.share.metrics.VellumHubMetrics;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CreatedUserPreferenceConsumerEvent {

    private static final String TOPIC = "created-user-preference";
    private static final String EVENT_TYPE = "CreatedUserPreferenceEvent";
    private static final String CONSUMER_GROUP = "recommendation_service_group";

    private final CreateUserProfileUseCase createUserProfileUseCase;
    private final VellumHubMetrics metrics;

    public CreatedUserPreferenceConsumerEvent(CreateUserProfileUseCase createUserProfileUseCase, VellumHubMetrics metrics) {
        this.createUserProfileUseCase = createUserProfileUseCase;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = "created-user-preference",
            groupId = "recommendation_service_group"
    )
    public void consume(@Payload CreatedUserPreferenceEvent event) {
        Timer.Sample sample = metrics.startKafkaProcessing();
        log.info(
                "Consumed CreatedUserPreferenceEvent. operation=kafka_consume, topic=created-user-preference, event_type=CreatedUserPreferenceEvent, userId={}, genreCount={}, aboutPresent={}",
                event.userId(),
                event.genres() == null ? 0 : event.genres().size(),
                event.about() != null && !event.about().isBlank()
        );

        try {
            var command = CreatedUserProfileCommand.of(
                    event.userId(),
                    event.genres(),
                    event.about()
            );
            createUserProfileUseCase.execute(command);
            metrics.recordKafkaConsumed(TOPIC, EVENT_TYPE, CONSUMER_GROUP);
            metrics.recordKafkaProcessingDuration(sample, TOPIC, EVENT_TYPE, CONSUMER_GROUP, "success");

            log.info("Successfully processed CreatedUserPreferenceEvent for userId: {}", event.userId());

        } catch (Exception e) {
            metrics.recordKafkaConsumeFailed(TOPIC, EVENT_TYPE, CONSUMER_GROUP);
            metrics.recordKafkaProcessingDuration(sample, TOPIC, EVENT_TYPE, CONSUMER_GROUP, "failure");
            log.error("Error processing CreatedUserPreferenceEvent: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao processar CreatedUserPreferenceEvent", e);
        }
    }


}
