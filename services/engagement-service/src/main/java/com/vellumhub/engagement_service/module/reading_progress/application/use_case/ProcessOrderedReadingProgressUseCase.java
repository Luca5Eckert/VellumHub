package com.vellumhub.engagement_service.module.reading_progress.application.use_case;
import com.vellumhub.engagement_service.module.reading_progress.application.port.ReadingProgressCheckpoint;
import com.vellumhub.engagement_service.module.reading_session_entry.application.command.CreateReadingSessionEntryCommand;
import com.vellumhub.engagement_service.module.reading_session_entry.application.use_case.CreateReadingSessionEntryUseCase;
import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ProcessOrderedReadingProgressUseCase {
    private final ReadingProgressCheckpoint checkpoint;
    private final CreateReadingSessionEntryUseCase history;
    public ProcessOrderedReadingProgressUseCase(ReadingProgressCheckpoint checkpoint,
                                                CreateReadingSessionEntryUseCase history) {
        this.checkpoint=checkpoint; this.history=history;
    }
    @Transactional
    public void execute(ReadingProgressChangedEvent event) {
        long last=checkpoint.lockAndRead(event.bookProgressId());
        if (event.revision() <= last) return;
        if (event.revision() != last+1) throw new IllegalStateException(
                "Missing reading-progress revision. aggregate=" + event.bookProgressId() +
                " expected=" + (last+1) + " received=" + event.revision());
        if (event.revision()==0 && event.kind()==ReadingProgressChangedEvent.Kind.BASELINE) {
            checkpoint.markApplied(event.bookProgressId(), 0, event.eventId());
            return;
        }
        if (event.revision()==0 && event.kind()!=ReadingProgressChangedEvent.Kind.CREATED)
            throw new IllegalArgumentException("First reading progress event must be CREATED");
        if (event.revision()>0 && event.kind()!=ReadingProgressChangedEvent.Kind.UPDATED)
            throw new IllegalArgumentException("Subsequent reading progress event must be UPDATED");
        history.execute(CreateReadingSessionEntryCommand.create(
                event.bookId(), event.bookProgressId(), event.userId(),
                event.progress(), event.newPage(), event.eventId(), event.occurredAt()));
        checkpoint.markApplied(event.bookProgressId(), event.revision(), event.eventId());
    }
}


