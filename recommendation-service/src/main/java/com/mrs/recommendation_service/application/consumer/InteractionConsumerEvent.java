package com.mrs.recommendation_service.application.consumer;

import com.mrs.recommendation_service.application.event.InteractionEvent;
import com.mrs.recommendation_service.domain.command.UpdateUserProfileCommand;
import com.mrs.recommendation_service.domain.handler.user_profile.UpdateUserProfileHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class InteractionConsumerEvent {

    private final UpdateUserProfileHandler updateUserProfileHandler;

    public InteractionConsumerEvent(UpdateUserProfileHandler updateUserProfileHandler) {
        this.updateUserProfileHandler = updateUserProfileHandler;
    }

    @KafkaListener(
            topics = "engagement-created",
            groupId = "recommendation-service"
    )
    public void consume(
            @Payload InteractionEvent interactionEvent
    ) {
        log.info("Evento recebido:  User={}, Media={}, Type={}",
                interactionEvent.userId(),
                interactionEvent.mediaId(),
                interactionEvent.interactionType());

        UpdateUserProfileCommand updateUserProfileCommand = new UpdateUserProfileCommand(
                interactionEvent.userId(),
                interactionEvent.mediaId(),
                interactionEvent.interactionType(),
                interactionEvent.interactionValue()
        );

        try {
            updateUserProfileHandler.execute(updateUserProfileCommand);
            log.info("Evento processado com sucesso");

        } catch (RuntimeException e) {
            log.error("Erro ao processar evento : {}",
                    e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Erro inesperado ao processar evento: {}",
                    e.getMessage(), e);
            throw new RuntimeException("Erro ao processar interação", e);
        }

    }

}