package com.mrs.engagement_service.domain.handler;

import com.mrs.engagement_service.domain.event.InteractionEvent;
import com.mrs.engagement_service.domain.exception.InvalidInteractionException;
import com.mrs.engagement_service.domain.model.Interaction;
import com.mrs.engagement_service.domain.port.EngagementRepository;
import org.springframework.stereotype.Component;

@Component
public class CreateEngagementHandler {

    private final EngagementRepository engagementRepository;

    private final KafkaTemplate<String, InteractionEvent> kafka;

    public CreateEngagementHandler(EngagementRepository engagementRepository, KafkaTemplate<String, InteractionEvent> kafka) {
        this.engagementRepository = engagementRepository;
        this.kafka = kafka;
    }

    public void handler(Interaction interaction){
        if(interaction == null) throw new InvalidInteractionException();

        engagementRepository.save(interaction);

        InteractionEvent interactionEvent = new InteractionEvent(
                interaction.getId(),
                interaction.getUserId(),
                interaction.getMediaId(),
                interaction.getType(),
                interaction.getInteractionValue(),
                interaction.getTimestamp()
        );

        kafka.send("engagement-created", interaction.getUserId().toString(), interactionEvent);
    }

}
