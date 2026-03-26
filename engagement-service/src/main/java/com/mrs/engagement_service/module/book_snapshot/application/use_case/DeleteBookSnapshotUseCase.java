package com.mrs.engagement_service.module.book_snapshot.application.use_case;

import com.mrs.engagement_service.module.book_snapshot.application.command.DeleteBookSnapshotCommand;
import com.mrs.engagement_service.module.book_snapshot.domain.port.BookSnapshotRepository;
import org.springframework.stereotype.Service;

@Service
public class DeleteBookSnapshotUseCase {

    private final BookSnapshotRepository bookSnapshotRepository;

    public DeleteBookSnapshotUseCase(BookSnapshotRepository bookSnapshotRepository) {
        this.bookSnapshotRepository = bookSnapshotRepository;
    }

    public void execute(DeleteBookSnapshotCommand command) {
        bookSnapshotRepository.deleteByBookId(command.bookId());
    }

}
