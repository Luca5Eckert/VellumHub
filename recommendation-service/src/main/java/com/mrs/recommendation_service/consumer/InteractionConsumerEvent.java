package com.mrs.recommendation_service.consumer;

import com.mrs.recommendation_service.event.InteractionEvent;
import com.mrs.recommendation_service.service.UserProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class InteractionConsumerEvent {

    private final UserProfileService userProfileService;

    public InteractionConsumerEvent(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @KafkaListener(
            topics = "engagement-created",
            groupId = "recommendation-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            @Payload InteractionEvent interactionEvent
    ) {
        log.info("Evento recebido:  User={}, Media={}, Type={}",
                interactionEvent.userId(),
                interactionEvent.mediaId(),
                interactionEvent.interactionType());

        try {
            userProfileService.update(interactionEvent);
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