package com.vellumhub.recommendation_service.module.user_profile.infrastructure.consumer;

import com.vellumhub.kafka.contracts.engagement.*;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.*;
import org.junit.jupiter.api.Test;
import com.vellumhub.recommendation_service.share.application.EventProcessingOutcome;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EngagementEventProcessorTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final UpdateUserProfileWithRatingUseCase rating=mock(UpdateUserProfileWithRatingUseCase.class);
    private final RemoveRatingFromProfileUseCase deletion=mock(RemoveRatingFromProfileUseCase.class);
    private final ReactionChangedUseCase reaction=mock(ReactionChangedUseCase.class);
    private final EngagementEventProcessor processor=new EngagementEventProcessor(jdbc,rating,deletion,reaction);

    @Test void baselineSetsCheckpointWithoutReapplyingHistoricWeight() {
        UUID user=UUID.randomUUID(),book=UUID.randomUUID(),eventId=UUID.randomUUID();
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(),any())).thenReturn(-1L);
        when(jdbc.update(anyString(),any(),any(),any(),any())).thenReturn(1);
        processor.apply(new RatingBaselineEvent(eventId,Instant.now(),42L,user,book,7));
        verifyNoInteractions(rating,deletion,reaction);
        verify(jdbc).update(contains("processed_engagement_events"),eq(eventId),any(),any(),any());
        verify(jdbc).update(contains("engagement_event_offsets SET last_revision"),eq(7L),eq("RATING"),eq("42"),eq(-1L));
    }

    @Test void duplicateRevisionDoesNotAdjustProfileAgain() {
        UUID user=UUID.randomUUID(),book=UUID.randomUUID();
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(),any())).thenReturn(0L);
        var fact=new RatingCreatedEvent(UUID.randomUUID(),Instant.now(),42L,user,book,5,0L);
        assertThat(processor.apply(fact)).isEqualTo(EventProcessingOutcome.DUPLICATE);
        verifyNoInteractions(rating,deletion,reaction);
        verify(jdbc,never()).update(contains("processed_engagement_events"),any(),any(),any(),any());
    }

    @Test void reusedEventIdCannotAcknowledgeAnUnappliedRevision() {
        UUID user=UUID.randomUUID(),book=UUID.randomUUID();
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(),any())).thenReturn(-1L);
        when(jdbc.update(contains("processed_engagement_events"),any(),any(),any(),any()))
                .thenReturn(0);
        var fact=new RatingCreatedEvent(UUID.randomUUID(),Instant.now(),42L,user,book,5,0L);
        assertThatThrownBy(() -> processor.apply(fact))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Event ID collision");
        verifyNoInteractions(rating,deletion,reaction);
        verify(jdbc,never()).update(contains("engagement_event_offsets SET last_revision"),
                any(),any(),any(),any());
    }

    @Test void revisionGapIsRejectedBeforeProfileMutation() {
        UUID user=UUID.randomUUID(),book=UUID.randomUUID();
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(),any())).thenReturn(1L);
        var fact=new RatingUpdatedEvent(UUID.randomUUID(),Instant.now(),42L,user,book,4,5,false,3L);
        assertThatThrownBy(()->processor.apply(fact)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Missing engagement revision");
        verifyNoInteractions(rating,deletion,reaction);
    }

    @Test void creationWithoutBaselineCannotStartAtArbitraryRevision() {
        UUID user=UUID.randomUUID(),book=UUID.randomUUID();
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(),any())).thenReturn(-1L);
        var fact=new RatingCreatedEvent(UUID.randomUUID(),Instant.now(),42L,user,book,5,3L);
        assertThatThrownBy(()->processor.apply(fact)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Missing engagement cutover baseline");
        verifyNoInteractions(rating,deletion,reaction);
    }
}

