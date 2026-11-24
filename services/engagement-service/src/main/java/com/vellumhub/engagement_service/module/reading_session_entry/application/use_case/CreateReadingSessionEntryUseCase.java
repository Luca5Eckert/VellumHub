package com.vellumhub.engagement_service.module.reading_session_entry.application.use_case;

import com.vellumhub.engagement_service.module.book_snapshot.domain.exception.BookSnapshotNotFoundException;
import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.book_snapshot.domain.port.BookSnapshotRepository;
import com.vellumhub.engagement_service.module.reading_session_entry.application.command.CreateReadingSessionEntryCommand;
import com.vellumhub.engagement_service.module.reading_session_entry.domain.model.ReadingSessionEntry;
import com.vellumhub.engagement_service.module.reading_session_entry.domain.port.ReadingSessionEntryRepository;
import com.vellumhub.engagement_service.module.reading_diary.infrastructure.ReadingDiaryStore;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class CreateReadingSessionEntryUseCase {

    private final ReadingSessionEntryRepository readingSessionEntryRepository;
    private final BookSnapshotRepository bookSnapshotRepository;
    private final ReadingDiaryStore diary;

    public CreateReadingSessionEntryUseCase(ReadingSessionEntryRepository readingSessionEntryRepository,
                                            BookSnapshotRepository bookSnapshotRepository, ReadingDiaryStore diary) {
        this.diary = diary;
        this.readingSessionEntryRepository = readingSessionEntryRepository;
        this.bookSnapshotRepository = bookSnapshotRepository;
    }

    @Transactional
    public void execute(CreateReadingSessionEntryCommand command){
        if (diary.lockAndIsDeleted(command.bookProgressId())) {
            return;
        }
        BookSnapshot bookSnapshot = bookSnapshotRepository.findById(command.bookId())
                .orElseThrow(BookSnapshotNotFoundException::new);

        var readingSessionEntry = ReadingSessionEntry.create(
                command.bookProgressId(),
                bookSnapshot,
                command.userId(),
                command.type(),
                command.pageRead(),
                command.eventId(),
                command.occurredAt()
        );

        readingSessionEntry.setReadAt(command.readAt());
        readingSessionEntry.setPageDelta(command.pageDelta());
        readingSessionEntryRepository.save(readingSessionEntry);
    }

}
