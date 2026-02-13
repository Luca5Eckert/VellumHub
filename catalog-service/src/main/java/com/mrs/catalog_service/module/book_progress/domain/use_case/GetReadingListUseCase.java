package com.mrs.catalog_service.module.book_progress.domain.use_case;

import com.mrs.catalog_service.module.book_progress.domain.model.ReadingStatus;
import com.mrs.catalog_service.module.book_progress.domain.port.BookProgressRepository;
import com.mrs.catalog_service.module.book_progress.domain.model.BookProgress;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class GetReadingListUseCase {

    private final BookProgressRepository bookProgressRepository;

    public GetReadingListUseCase(BookProgressRepository bookProgressRepository) {
        this.bookProgressRepository = bookProgressRepository;
    }

    public List<BookProgress> execute(UUID userId) {
        return bookProgressRepository.findAllByUserIdAndStatus(userId, ReadingStatus.READING);
    }

}
