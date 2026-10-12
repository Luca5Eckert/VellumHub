package com.vellumhub.engagement_service.module.reaction.application.event;

import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.engagement_service.module.outbox.application.port.OutboxWriter;
import com.vellumhub.engagement_service.module.reaction.domain.model.Reaction;
import com.vellumhub.engagement_service.module.reaction.domain.model.TypeReaction;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.ReactionChangedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReactionEventRecorderTest {
    @Test void outboxCapturesTransitionWithRevisionAndProvenance() {
        var outbox=mock(OutboxWriter.class);
        var recorder=new ReactionEventRecorder(outbox);
        var reaction=Reaction.builder().id(8L).version(4)
                .userId(UUID.randomUUID()).bookSnapshot(new BookSnapshot(UUID.randomUUID()))
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .typeReaction(TypeReaction.VERY_POSITIVE).build();
        recorder.changed(reaction,TypeReaction.POSITIVE);
        var cap=ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outbox).append(cap.capture());
        assertThat(cap.getValue().topic()).isEqualTo(KafkaTopics.ENGAGEMENT_REACTIONS);
        assertThat(cap.getValue().aggregateType()).isEqualTo("REACTION");
        assertThat(cap.getValue().revision()).isEqualTo(4);
        var event=(ReactionChangedEvent)cap.getValue().payload();
        assertThat(event.oldTypeReaction()).isEqualTo("POSITIVE");
        assertThat(event.newTypeReaction()).isEqualTo("VERY_POSITIVE");
        assertThat(event.revision()).isEqualTo(4L);
    }
}

