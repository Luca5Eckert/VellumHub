package com.vellumhub.recommendation_service.module.reading_progress.application.use_case;
import com.vellumhub.recommendation_service.module.reading_progress.application.port.ReadingProgressCheckpoint;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.user_profile.application.command.UpdateBookProgressCommand;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.UpdateBookProgressUseCase;
import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
import com.vellumhub.recommendation_service.share.application.EventProcessingOutcome;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Checkpoint and vector update commit or roll back atomically. */
@Service
public class ProcessOrderedReadingProgressUseCase {
    private final ReadingProgressCheckpoint checkpoint;
    private final BookFeatureRepository features;
    private final UpdateBookProgressUseCase profile;
    public ProcessOrderedReadingProgressUseCase(ReadingProgressCheckpoint checkpoint,
             BookFeatureRepository features, UpdateBookProgressUseCase profile) {
        this.checkpoint=checkpoint; this.features=features; this.profile=profile;
    }
    @Transactional
    public EventProcessingOutcome execute(ReadingProgressChangedEvent event) {
        long last=checkpoint.lockAndRead(event.bookProgressId());
        if (event.revision() <= last) return EventProcessingOutcome.DUPLICATE;
        if (event.revision() != last+1) throw new IllegalStateException(
                "Reading progress gap: " + event.bookProgressId() +
                " expected=" + (last+1) + " received=" + event.revision());
        if (event.revision()==0 && event.kind()==ReadingProgressChangedEvent.Kind.BASELINE) {
            checkpoint.markApplied(event.bookProgressId(), 0, event.eventId());
            return EventProcessingOutcome.APPLIED;
        }
        if (event.revision()==0 && event.kind()!=ReadingProgressChangedEvent.Kind.CREATED)
            throw new IllegalArgumentException("First revision must be CREATED");
        if (event.revision()>0 && event.kind()!=ReadingProgressChangedEvent.Kind.UPDATED)
            throw new IllegalArgumentException("Later revisions must be UPDATED");
        // The legacy use case silently skips missing book features. The ordered path must
        // NOT commit its offset in that situation, or the profile would permanently miss the signal.
        if (features.findById(event.bookId()).isEmpty())
            throw new IllegalStateException("Book features not yet available: " + event.bookId());
        profile.execute(UpdateBookProgressCommand.of(
                event.userId(), event.bookId(), event.progress(), event.oldPage(), event.newPage()));
        checkpoint.markApplied(event.bookProgressId(), event.revision(), event.eventId());
        return EventProcessingOutcome.APPLIED;
    }
}


