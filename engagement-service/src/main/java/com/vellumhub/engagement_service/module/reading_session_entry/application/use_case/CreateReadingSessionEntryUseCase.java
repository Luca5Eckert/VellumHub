package com.vellumhub.engagement_service.module.reading_session_entry.application.use_case;

import com.vellumhub.engagement_service.module.book_snapshot.domain.exception.BookSnapshotNotFoundException;
import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.book_snapshot.domain.port.BookSnapshotRepository;
import com.vellumhub.engagement_service.module.reading_session_entry.application.command.CreateReadingSessionEntryCommand;
import com.vellumhub.engagement_service.module.reading_session_entry.domain.event.CreateReadingSessionEvent;
import com.vellumhub.engagement_service.module.reading_session_entry.domain.model.ReadingSessionEntry;
import com.vellumhub.engagement_service.module.reading_session_entry.domain.port.ReadingSessionEntryRepository;
import com.vellumhub.engagement_service.module.reading_session_entry.domain.port.ReadingSessionEventPublisher;
import com.vellumhub.engagement_service.share.port.RequestContext;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CreateReadingSessionEntryUseCase {

    private final ReadingSessionEntryRepository readingSessionEntryRepository;
    private final BookSnapshotRepository bookSnapshotRepository;

    private final RequestContext requestContext;
    private final ReadingSessionEventPublisher eventPublisher;

    public CreateReadingSessionEntryUseCase(ReadingSessionEntryRepository readingSessionEntryRepository, BookSnapshotRepository bookSnapshotRepository, RequestContext requestContext, ReadingSessionEventPublisher eventPublisher) {
        this.readingSessionEntryRepository = readingSessionEntryRepository;
        this.bookSnapshotRepository = bookSnapshotRepository;
        this.requestContext = requestContext;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void execute(CreateReadingSessionEntryCommand command){
        BookSnapshot bookSnapshot = bookSnapshotRepository.findById(command.bookId())
                .orElseThrow(BookSnapshotNotFoundException::new);

        UUID userId = requestContext.getUserId();

        var readingSessionEntry = ReadingSessionEntry.create(
                bookSnapshot,
                userId,
                command.readingSessionType()
        );

        readingSessionEntryRepository.save(readingSessionEntry);

        var event = CreateReadingSessionEvent.of(readingSessionEntry);
        eventPublisher.publish(event);

    }

}
