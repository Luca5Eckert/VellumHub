package com.vellumhub.engagement_service.module.book_snapshot.application.use_case;

import com.vellumhub.engagement_service.module.book_snapshot.application.command.CreateBookSnapshotCommand;
import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.book_snapshot.domain.port.BookSnapshotRepository;
import org.springframework.stereotype.Service;

@Service
public class CreateBookSnapshotUseCase {

    private final BookSnapshotRepository bookSnapshotRepository;

    public CreateBookSnapshotUseCase(BookSnapshotRepository bookSnapshotRepository) {
        this.bookSnapshotRepository = bookSnapshotRepository;
    }

    public void execute(CreateBookSnapshotCommand command) {
        var bookSnapshot = new BookSnapshot(command.bookId());

        bookSnapshotRepository.save(bookSnapshot);
    }
}
