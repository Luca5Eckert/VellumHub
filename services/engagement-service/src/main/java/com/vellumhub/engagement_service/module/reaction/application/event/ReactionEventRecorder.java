package com.vellumhub.engagement_service.module.reaction.application.event;

import com.vellumhub.engagement_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.engagement_service.module.outbox.application.port.OutboxWriter;
import com.vellumhub.engagement_service.module.reaction.domain.model.Reaction;
import com.vellumhub.engagement_service.module.reaction.domain.model.TypeReaction;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.ReactionChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import java.util.UUID;

/** Records the persisted reaction transition, never a request to modify Recommendation. */
@Slf4j
@Component
public class ReactionEventRecorder {
    private final OutboxWriter outbox;
    private final boolean avro;
    @Autowired
    public ReactionEventRecorder(OutboxWriter outbox,
            @Value("${app.engagement.reactions.wire-format:json}") String format) {
        this.outbox=outbox;
        if (!"json".equalsIgnoreCase(format) && !"avro".equalsIgnoreCase(format))
            throw new IllegalArgumentException("Unknown Reaction wire format");
        this.avro="avro".equalsIgnoreCase(format);
    }
    public ReactionEventRecorder(OutboxWriter outbox) { this(outbox,"json"); }
    public void changed(Reaction reaction,TypeReaction previous) {
        var event=new ReactionChangedEvent(UUID.randomUUID(),
                previous==null?reaction.getCreatedAt():reaction.getUpdatedAt(),
                reaction.getId(),reaction.getUserId(),reaction.getBookSnapshot().getBookId(),
                previous==null?null:previous.name(),reaction.getTypeReaction().name(),
                reaction.getTypeReaction().name(),reaction.getVersion());
        outbox.append(new OutboxMessage(event.eventId(),"REACTION",reaction.getId().toString(),
                reaction.getVersion(),"ReactionChangedEvent",
                avro ? KafkaTopics.ENGAGEMENT_REACTIONS_AVRO : KafkaTopics.ENGAGEMENT_REACTIONS,
                reaction.getUserId().toString(),event,avro ? "AVRO" : "JSON",1));
        log.debug("operation=engagement_event_enqueued eventId={} reactionId={} revision={}",
                event.eventId(),event.reactionId(),event.revision());
    }
}

