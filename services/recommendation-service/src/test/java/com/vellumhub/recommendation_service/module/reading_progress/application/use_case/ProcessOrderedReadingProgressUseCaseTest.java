package com.vellumhub.recommendation_service.module.reading_progress.application.use_case;
import com.vellumhub.recommendation_service.module.reading_progress.application.port.ReadingProgressCheckpoint;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.UpdateBookProgressUseCase;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProcessOrderedReadingProgressUseCaseTest {
    private final ReadingProgressCheckpoint checkpoint=mock(ReadingProgressCheckpoint.class);
    private final UpdateBookProgressUseCase effect=mock(UpdateBookProgressUseCase.class);
    private final BookFeatureRepository features=mock(BookFeatureRepository.class);
    private final ProcessOrderedReadingProgressUseCase process=
        new ProcessOrderedReadingProgressUseCase(checkpoint,features,effect);
    private ReadingProgressChangedEvent event(long revision, ReadingProgressChangedEvent.Kind kind) {
        return new ReadingProgressChangedEvent(UUID.randomUUID(),Instant.now(),UUID.randomUUID(),
            UUID.randomUUID(),UUID.randomUUID(),"READING",10,20,revision,kind);
    }
    @Test void duplicateDoesNotRepeatEffect() {
        var e=event(1,ReadingProgressChangedEvent.Kind.UPDATED);
        when(checkpoint.lockAndRead(e.bookProgressId())).thenReturn(1L);
        process.execute(e);
        verifyNoInteractions(effect);
        verify(checkpoint,never()).markApplied(any(),anyLong(),any());
    }
    @Test void gapIsRejectedRatherThanAppliedOutOfOrder() {
        var e=event(3,ReadingProgressChangedEvent.Kind.UPDATED);
        when(checkpoint.lockAndRead(e.bookProgressId())).thenReturn(0L);
        assertThatThrownBy(()->process.execute(e)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(effect);
    }
    @Test void baselineAdvancesOnlyCheckpoint() {
        var e=event(0,ReadingProgressChangedEvent.Kind.BASELINE);
        when(checkpoint.lockAndRead(e.bookProgressId())).thenReturn(-1L);
        process.execute(e);
        verify(checkpoint).markApplied(e.bookProgressId(),0L,e.eventId());
        verifyNoInteractions(effect);
    }
    @Test void nextRevisionIsApplied() {
        var e=event(1,ReadingProgressChangedEvent.Kind.UPDATED);
        when(checkpoint.lockAndRead(e.bookProgressId())).thenReturn(0L);
        when(features.findById(e.bookId())).thenReturn(Optional.of(mock(BookFeature.class)));
        process.execute(e);
        verify(checkpoint).markApplied(e.bookProgressId(),1L,e.eventId());
        verify(effect).execute(any());
    }
}

