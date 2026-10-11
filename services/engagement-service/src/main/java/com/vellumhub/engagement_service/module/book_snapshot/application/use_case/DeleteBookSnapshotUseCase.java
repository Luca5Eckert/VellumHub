package com.vellumhub.engagement_service.module.book_snapshot.application.use_case;

import com.vellumhub.engagement_service.module.book_snapshot.application.command.DeleteBookSnapshotCommand;
import com.vellumhub.engagement_service.module.book_snapshot.domain.port.BookSnapshotRepository;
import org.springframework.stereotype.Service;

@Service
public class DeleteBookSnapshotUseCase {

    private final BookSnapshotRepository bookSnapshotRepository;

    public DeleteBookSnapshotUseCase(BookSnapshotRepository bookSnapshotRepository) {
        this.bookSnapshotRepository = bookSnapshotRepository;
    }

    public void execute(DeleteBookSnapshotCommand command) {
        // Legacy delete topics must also preserve identities referenced by historical facts.
        bookSnapshotRepository.findById(command.bookId()).ifPresent(snapshot -> {
            snapshot.setActive(false);
            bookSnapshotRepository.save(snapshot);
        });
    }

}
