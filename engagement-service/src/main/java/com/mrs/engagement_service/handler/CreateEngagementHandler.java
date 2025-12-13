package com.mrs.engagement_service.handler;

import com.mrs.engagement_service.event.InteractionEvent;
import com.mrs.engagement_service.model.Interaction;
import com.mrs.engagement_service.repository.EngagementRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CreateEngagementHandler {

    private final EngagementRepository engagementRepository;

    private final KafkaTemplate<String, InteractionEvent> kafka;

    public CreateEngagementHandler(EngagementRepository engagementRepository, KafkaTemplate<String, InteractionEvent> kafka) {
        this.engagementRepository = engagementRepository;
        this.kafka = kafka;
    }

    public void handler(Interaction interaction){
        if(interaction == null) throw new IllegalArgumentException("Interaction can't be null");

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
