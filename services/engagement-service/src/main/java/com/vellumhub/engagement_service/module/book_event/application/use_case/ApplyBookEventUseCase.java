package com.vellumhub.engagement_service.module.book_event.application.use_case;
import com.vellumhub.engagement_service.module.book_event.application.port.BookEventCheckpoint;
import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.book_snapshot.domain.port.BookSnapshotRepository;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Minimal identity projection; the full snapshot remains on Kafka for other subscribers. */
@Slf4j
@Service
public class ApplyBookEventUseCase {
    private final BookEventCheckpoint checkpoint;
    private final BookSnapshotRepository snapshots;
    public ApplyBookEventUseCase(BookEventCheckpoint checkpoint, BookSnapshotRepository snapshots) {
        this.checkpoint=checkpoint; this.snapshots=snapshots;
    }
    @Transactional
    public void execute(BookChangedEvent event) {
        long previous=checkpoint.lockAndRead(event.bookId());
        if (event.revision()<=previous) {
            log.debug("operation=book_event_duplicate, bookId={}, revision={}",event.bookId(),event.revision());
            return;
        }
        if (event.kind()!=BookChangedEvent.Kind.BASELINE || previous!=-1) {
            if (event.revision()!=previous+1 || event.kind()==BookChangedEvent.Kind.BASELINE)
                throw new IllegalStateException("Book revision gap: id="+event.bookId()+
                        " expected="+(previous+1)+" actual="+event.revision());
            if (previous==-1 && event.kind()!=BookChangedEvent.Kind.CREATED)
                throw new IllegalStateException("Initial book event must be CREATED: "+event.bookId());
        }
        if (event.kind()==BookChangedEvent.Kind.DELETED) {
            // Tombstone means inactive in this read model; the identity is referenced by history.
            snapshots.findById(event.bookId()).ifPresent(snapshot -> {
                snapshot.setActive(false);
                snapshots.save(snapshot);
            });
        } else {
            BookSnapshot snapshot = snapshots.findById(event.bookId())
                    .orElseGet(()->new BookSnapshot(event.bookId()));
            snapshot.setActive(true);
            snapshots.save(snapshot);
        }
        checkpoint.applied(event.bookId(),previous,event.revision());
    }
}

