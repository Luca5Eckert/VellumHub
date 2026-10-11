package com.vellumhub.engagement_service.module.book_event.application.use_case;

import com.vellumhub.engagement_service.module.book_event.application.port.BookEventCheckpoint;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

import com.vellumhub.engagement_service.module.book_snapshot.domain.port.BookSnapshotRepository;
import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;

class ApplyBookEventUseCaseTest {
    private final BookEventCheckpoint checkpoint=mock(BookEventCheckpoint.class);
    private final BookSnapshotRepository snapshots=mock(BookSnapshotRepository.class);
    private final ApplyBookEventUseCase process=new ApplyBookEventUseCase(checkpoint,snapshots);

    private BookChangedEvent event(UUID id,long rev,BookChangedEvent.Kind kind) {
        var state = kind==BookChangedEvent.Kind.DELETED?null:
            new BookChangedEvent.State("Dune","SF",1965,null,"Herbert","ISBN",400,"Ace",List.of("SF"));
        return new BookChangedEvent(UUID.randomUUID(),Instant.now(),id,rev,kind,state);
    }

    @Test void baselineOnlyReconcilesSnapshotWithoutFabricatingCreation() {
        UUID id=UUID.randomUUID();
        var event=event(id,4,BookChangedEvent.Kind.BASELINE);
        when(checkpoint.lockAndRead(id)).thenReturn(-1L);
        process.execute(event);
        verify(snapshots).save(any(BookSnapshot.class));
        verify(checkpoint).applied(id,-1,4);
    }

    @Test void duplicateDoesNotReplayProjection() {
        UUID id=UUID.randomUUID();
        when(checkpoint.lockAndRead(id)).thenReturn(2L);
        process.execute(event(id,2,BookChangedEvent.Kind.UPDATED));
        verifyNoInteractions(snapshots);
        verify(checkpoint,never()).applied(any(),anyLong(),anyLong());
    }

    @Test void missingRevisionCannotAdvance() {
        UUID id=UUID.randomUUID();
        when(checkpoint.lockAndRead(id)).thenReturn(0L);
        assertThatThrownBy(()->process.execute(event(id,2,BookChangedEvent.Kind.UPDATED)))
            .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(snapshots);
    }

    @Test void deletionRemovesProjectionWithoutDeletingCatalogRow() {
        UUID id=UUID.randomUUID();
        when(checkpoint.lockAndRead(id)).thenReturn(1L);
        var saved=new BookSnapshot(id);
        when(snapshots.findById(id)).thenReturn(Optional.of(saved));
        process.execute(event(id,2,BookChangedEvent.Kind.DELETED));
        assertThat(saved.isActive()).isFalse();
        verify(snapshots).save(saved);
        verify(snapshots,never()).deleteByBookId(id);
        verify(checkpoint).applied(id,1,2);
    }
}

