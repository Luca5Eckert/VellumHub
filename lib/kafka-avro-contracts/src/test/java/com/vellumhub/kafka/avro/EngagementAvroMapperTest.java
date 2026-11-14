package com.vellumhub.kafka.avro;

import com.vellumhub.kafka.contracts.engagement.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EngagementAvroMapperTest {
    @Test void allSixCanonicalFactsPreserveIdentityAndRevision() {
        var id=UUID.randomUUID();
        var user=UUID.randomUUID();
        var book=UUID.randomUUID();
        var date=Instant.parse("2026-11-10T12:00:00Z");
        var facts = new Object[] {
            new RatingCreatedEvent(id,date,23L,user,book,5,0L),
            new RatingUpdatedEvent(id,date,23L,user,book,4,5,false,1L),
            new RatingDeletedEvent(id,date,23L,user,book,5,2L),
            new RatingBaselineEvent(id,date,23L,user,book,0L),
            new ReactionChangedEvent(id,date,41L,user,book,null,"POSITIVE","POSITIVE",0L),
            new ReactionBaselineEvent(id,date,41L,user,book,0L)
        };
        for (Object fact : facts) {
            var wire=EngagementAvroMapper.toAvro(fact);
            assertEquals(fact,EngagementAvroMapper.fromAvro(user.toString(),wire),wire.getClass().getName());
            assertThrows(IllegalArgumentException.class,
                    () -> EngagementAvroMapper.fromAvro(UUID.randomUUID().toString(),wire));
        }
    }

    @Test void rejectsAliasDriftAndUnversionedReactions() {
        var event=new ReactionChangedEvent(UUID.randomUUID(),Instant.now(),13L,
                UUID.randomUUID(),UUID.randomUUID(),null,"POSITIVE","NEGATIVE",1L);
        assertThrows(IllegalArgumentException.class,()->EngagementAvroMapper.toAvro(event));
    }
}
