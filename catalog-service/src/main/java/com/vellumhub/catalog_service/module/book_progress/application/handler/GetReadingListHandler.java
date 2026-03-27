package com.mrs.catalog_service.module.book_progress.application.handler;

import com.mrs.catalog_service.module.book_progress.application.dto.BookProgressResponse;
import com.mrs.catalog_service.module.book_progress.application.mapper.BookProgressMapper;
import com.mrs.catalog_service.module.book_progress.domain.use_case.GetReadingListUseCase;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class GetReadingListHandler {

    private final GetReadingListUseCase getReadingListUseCase;

    private final BookProgressMapper bookProgressMapper;

    public GetReadingListHandler(GetReadingListUseCase getReadingListUseCase, BookProgressMapper bookProgressMapper) {
        this.getReadingListUseCase = getReadingListUseCase;
        this.bookProgressMapper = bookProgressMapper;
    }

    public List<BookProgressResponse> handle(UUID userId) {
        var readingList = getReadingListUseCase.execute(userId);

        return readingList.stream()
                .map(bookProgressMapper::toResponse)
                .toList();
    }

}
