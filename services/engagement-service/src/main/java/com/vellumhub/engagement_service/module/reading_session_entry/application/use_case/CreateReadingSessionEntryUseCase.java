package com.vellumhub.engagement_service.module.reading_session_entry.application.use_case;

import com.vellumhub.engagement_service.module.book_snapshot.domain.exception.BookSnapshotNotFoundException;
import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.book_snapshot.domain.port.BookSnapshotRepository;
import com.vellumhub.engagement_service.module.reading_session_entry.application.command.CreateReadingSessionEntryCommand;
import com.vellumhub.engagement_service.module.reading_session_entry.domain.model.ReadingSessionEntry;
import com.vellumhub.engagement_service.module.reading_session_entry.domain.port.ReadingSessionEntryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class CreateReadingSessionEntryUseCase {

    private final ReadingSessionEntryRepository readingSessionEntryRepository;
    private final BookSnapshotRepository bookSnapshotRepository;

    public CreateReadingSessionEntryUseCase(ReadingSessionEntryRepository readingSessionEntryRepository, BookSnapshotRepository bookSnapshotRepository) {
        this.readingSessionEntryRepository = readingSessionEntryRepository;
        this.bookSnapshotRepository = bookSnapshotRepository;
    }

    @Transactional
    public void execute(CreateReadingSessionEntryCommand command){
        BookSnapshot bookSnapshot = bookSnapshotRepository.findById(command.bookId())
                .orElseThrow(BookSnapshotNotFoundException::new);

        var readingSessionEntry = ReadingSessionEntry.create(
                command.bookProgressId(),
                bookSnapshot,
                command.userId(),
                command.type(),
                command.pageRead()
        );

        readingSessionEntryRepository.save(readingSessionEntry);
    }

}
