package com.mrs.engagement_service.module.book_snapshot.application.use_case;

import com.mrs.engagement_service.module.book_snapshot.application.command.CreateBookSnapshotCommand;
import com.mrs.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.mrs.engagement_service.module.book_snapshot.domain.port.BookSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateBookSnapshotUseCase {

    private final BookSnapshotRepository bookSnapshotRepository;

    public CreateBookSnapshotUseCase(BookSnapshotRepository bookSnapshotRepository) {
        this.bookSnapshotRepository = bookSnapshotRepository;
    }

    @Transactional
    public void execute(CreateBookSnapshotCommand command) {
        var bookSnapshot = new BookSnapshot(command.bookId());

        bookSnapshotRepository.save(bookSnapshot);
    }
}
