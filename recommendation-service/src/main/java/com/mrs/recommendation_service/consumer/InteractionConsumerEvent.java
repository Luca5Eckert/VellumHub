package com.mrs.recommendation_service.consumer;

import com.mrs.recommendation_service.event.InteractionEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InteractionConsumerEvent {

    @KafkaListener(topics = "engagement-created")
    public void consume(InteractionEvent interactionEvent){
        System.out.println("Recebido: " + interactionEvent);
    }

}
